package codes.honor.cobblemondynamics.handler.xp

import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.engine.WorkArea
import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.handler.item.QueryClock
import codes.honor.cobblemondynamics.handler.vanilla.TickClock
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.pasture.PastureAdapter
import codes.honor.cobblemondynamics.xp.ExperienceCollectorBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * The Soul Collector: a Ghost-type takes the XP orbs of its pasture's work area into its bound Experience Collector, in one batch
 * every 20 ticks (CD-SOU-04, CD-SOU-06). It leaves every orb that a player could take (CD-SOU-05) and, for 6,000 ticks, every orb near
 * the spot where a player died (CD-SOU-12), and it moves only XP that already exists, point for point (CD-SOU-07). It looks for orbs at
 * most once every 20 ticks (CD-SOU-09), as the Magnet looks for items, and checks each orb again when it takes it.
 */
object SoulCollectorHandler : TargetHandler {
    override val id: ResourceLocation = id("soul_collector")

    /** Leash only (CD-SOU-01), so no scan looks for its targets. */
    override val scanKind: ScanKind = ScanKind.NONE

    /** CD-SOU-03: up to `logistics.maxWorkersPerContainer` Soul Collectors share one collector. A collector holds no items. */
    override val sharesTargets: Boolean = true

    private val COLLECTOR: ResourceLocation = id("experience_collector")

    /** CD-SOU-02: the Experience Collector, and no other block: no chest, no tank of another mod, no tank of `c:experience`. */
    fun isTarget(block: ResourceLocation): Boolean = block == COLLECTOR

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        isTarget(BuiltInRegistries.BLOCK.getKey(state.block)) && blockEntity is ExperienceCollectorBlockEntity

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = collector(level, canonical) != null

    /**
     * CD-SOU-08: an orb in the work area that the collector has room for. A worker with nothing to take waits in Standby. Each orb keeps
     * the takeable flag of the last query, so a tick between two queries reads no player.
     */
    override fun hasDemand(ctx: WorkContext): Boolean {
        val collector = collector(ctx.level, ctx.target) ?: return false
        val room = collector.store.room
        val params = SoulCollectorParams.of(ctx.role.params)
        if (room <= 0 || params.unitsPerBatch(ctx.pokemon.level) <= 0) return false
        val area = area(ctx) ?: return false
        val found = found(ctx, area, room, params.playerDistance, null)
        return found.orbs.indices.any { found.takeable[it] && inArea(found.orbs[it], area) }
    }

    override fun onStart(ctx: WorkContext) {
        memo(ctx).startedAt = ctx.now
    }

    /**
     * CD-SOU-06, CD-SOU-07: a batch on the first Working tick and every 20 ticks after it, over the orbs of the last query in its order,
     * each as it is in this tick, and the players near them now. The batch reads the capacity once, so a config reload in the batch
     * changes nothing. It puts each take into the collector first, and changes the orb only by the XP that the collector took.
     */
    override fun applyWork(ctx: WorkContext) {
        if (!TickClock.every(SoulCollectorParams.BATCH_TICKS, memo(ctx).startedAt, ctx.now)) return
        val collector = collector(ctx.level, ctx.target) ?: return
        val area = area(ctx) ?: return
        val params = SoulCollectorParams.of(ctx.role.params)
        val capacity = collector.store.capacity()
        val room = collector.store.roomUnder(capacity)
        if (room <= 0) return
        val near = near(ctx, area, params.playerDistance)
        val found = found(ctx, area, room, params.playerDistance, near)
        val orbs = ArrayList<ExperienceOrb>(found.orbs.size)
        val facts = ArrayList<OrbFacts>(found.orbs.size)
        for (i in found.orbs.indices) {
            val orb = found.orbs[i]
            if (!inArea(orb, area)) continue
            orbs += orb
            facts += found.facts[i].copy(value = orb.value, count = orb.count, playerWithinSqr = playerWithin(orb, near))
        }
        val index = facts.indices.associateBy { facts[it].id }
        for (planned in OrbRules.takes(facts, params.unitsPerBatch(ctx.pokemon.level), room, params.playerDistance)) {
            val i = index[planned.id] ?: continue
            val added = collector.insert(planned.xp, capacity)
            val take = OrbRules.take(facts[i], planned.units, added)
            // A part of a unit cannot leave a merged orb, so XP that the cut take does not hold goes back out of the collector.
            if (take.xp < added) collector.store.extract(added - take.xp, simulate = false)
            val orb = orbs[i]
            orb.count = take.count
            orb.value = take.value
            if (take.emptied) orb.discard()
        }
    }

    private fun collector(level: ServerLevel, pos: BlockPos): ExperienceCollectorBlockEntity? =
        level.getBlockEntity(pos) as? ExperienceCollectorBlockEntity

    /** The claim's batch start, its query clock, and what the last query found. */
    private class Memo(var startedAt: Long) {
        val clock = QueryClock(SoulCollectorParams.BATCH_TICKS)
        var found: Found = Found.NONE
    }

    /** The orbs of one query, nearest first, their facts at the query, and whether each was takeable then. */
    private class Found(val orbs: List<ExperienceOrb>, val facts: List<OrbFacts>, val takeable: BooleanArray) {
        companion object {
            val NONE = Found(emptyList(), emptyList(), BooleanArray(0))
        }
    }

    /** The players and the fresh death spots near the work area, read once per call. */
    private class Near(val players: List<Player>, val deaths: List<DeathSpots.Spot>)

    private fun memo(ctx: WorkContext): Memo = ctx.memo.value as? Memo ?: Memo(ctx.now).also { ctx.memo.value = it }

    /** CD-SOU-04: the work area of the worker's own pasture, as for the Magnet (CD-MAG-04). */
    private fun area(ctx: WorkContext): WorkArea? =
        ctx.pasture?.let { WorkArea.of(it, WorkEngineSettings.current, PastureAdapter.maxWanderDistance()) }

    /**
     * CD-SOU-09: one query of the work area, at most once every 20 ticks. The query sorts its orbs nearest first, once, and keeps whether
     * each was takeable with [room] until the next query. It reads [near] only when it runs and finds an orb, or uses the caller's.
     */
    private fun found(ctx: WorkContext, area: WorkArea, room: Int, playerDistance: Double, near: Near?): Found {
        val memo = memo(ctx)
        if (!memo.clock.isDue(ctx.now)) return memo.found
        memo.clock.ran(ctx.now)
        val orbs = ctx.level.getEntitiesOfClass(ExperienceOrb::class.java, area.box()) { inArea(it, area) }
        if (orbs.isEmpty()) {
            memo.found = Found.NONE
            return memo.found
        }
        val around = near ?: near(ctx, area, playerDistance)
        val byId = orbs.associateBy { it.id }
        val facts = OrbRules.nearestFirst(
            orbs.map { OrbFacts(it.id, it.distanceToSqr(ctx.worker), it.value, it.count, playerWithin(it, around)) },
        )
        val takeable = BooleanArray(facts.size) { OrbRules.takeable(facts[it], room, playerDistance) }
        memo.found = Found(facts.map { byId.getValue(it.id) }, facts, takeable)
        return memo.found
    }

    private fun inArea(orb: ExperienceOrb, area: WorkArea): Boolean = orb.isAlive && area.contains(orb.blockPosition())

    /**
     * CD-SOU-05, CD-SOU-12: one entity query for the players within [playerDistance] of the work area, which leaves out spectators as
     * [OrbRules.nearestPlayer] does, and the fresh death spots of the same box.
     */
    private fun near(ctx: WorkContext, area: WorkArea, playerDistance: Double): Near {
        val box = area.box().inflate(playerDistance)
        val deaths = PlayerDeaths.spots.within(ctx.level.dimension().location(), box, ctx.level.gameTime)
        return Near(ctx.level.getEntitiesOfClass(Player::class.java, box), deaths)
    }

    /** The square of the distance from [orb] to the nearest player or fresh death spot of [near], or null when there is none. */
    private fun playerWithin(orb: ExperienceOrb, near: Near): Double? {
        if (near.players.isEmpty() && near.deaths.isEmpty()) return null
        return OrbRules.nearestPlayer(
            near.players.map { NearbyPlayer(orb.distanceToSqr(it), it.isSpectator) },
            near.deaths.map { orb.distanceToSqr(it.x, it.y, it.z) },
        )
    }
}
