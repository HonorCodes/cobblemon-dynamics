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
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * The Soul Collector: a Ghost-type takes the XP orbs of its pasture's work area into its bound Experience Collector, in one batch
 * every 20 ticks (CD-SOU-04, CD-SOU-06). It leaves every orb that a player could take (CD-SOU-05), and it moves only XP that already
 * exists, point for point (CD-SOU-07). It looks for orbs at most once every 20 ticks (CD-SOU-09), as the Magnet looks for items, and
 * checks each orb again when it takes it.
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

    /** CD-SOU-08: an orb in the work area that the collector has room for. A worker with nothing to take waits in Standby. */
    override fun hasDemand(ctx: WorkContext): Boolean {
        val collector = collector(ctx.level, ctx.target) ?: return false
        val room = collector.store.room
        val params = SoulCollectorParams.of(ctx.role.params)
        if (room <= 0 || params.unitsPerBatch(ctx.pokemon.level) <= 0) return false
        val area = area(ctx) ?: return false
        val players = players(ctx)
        return orbs(ctx, area).any { OrbRules.takeable(facts(ctx, it, players), room, params.playerDistance) }
    }

    override fun onStart(ctx: WorkContext) {
        memo(ctx).startedAt = ctx.now
    }

    /** CD-SOU-06, CD-SOU-07: a batch on the first Working tick and every 20 ticks after it, from the orbs as they are in that tick. */
    override fun applyWork(ctx: WorkContext) {
        if (!TickClock.every(SoulCollectorParams.BATCH_TICKS, memo(ctx).startedAt, ctx.now)) return
        val collector = collector(ctx.level, ctx.target) ?: return
        val area = area(ctx) ?: return
        val params = SoulCollectorParams.of(ctx.role.params)
        val players = players(ctx)
        val orbs = orbs(ctx, area)
        val byId = orbs.associateBy { it.id }
        val facts = orbs.map { facts(ctx, it, players) }
        for (take in OrbRules.takes(facts, params.unitsPerBatch(ctx.pokemon.level), collector.store.room, params.playerDistance)) {
            val orb = byId[take.id] ?: continue
            orb.count = take.count
            orb.value = take.value
            if (take.emptied) orb.discard()
            collector.insert(take.xp)
        }
    }

    private fun collector(level: ServerLevel, pos: BlockPos): ExperienceCollectorBlockEntity? =
        level.getBlockEntity(pos) as? ExperienceCollectorBlockEntity

    /** The claim's batch start, and its query clock with what the last query found. */
    private class Memo(var startedAt: Long) {
        val clock = QueryClock(SoulCollectorParams.BATCH_TICKS)
        var found: List<ExperienceOrb> = emptyList()
    }

    private fun memo(ctx: WorkContext): Memo = ctx.memo.value as? Memo ?: Memo(ctx.now).also { ctx.memo.value = it }

    /** CD-SOU-04: the work area of the worker's own pasture, as for the Magnet (CD-MAG-04). */
    private fun area(ctx: WorkContext): WorkArea? =
        ctx.pasture?.let { WorkArea.of(it, WorkEngineSettings.current, PastureAdapter.maxWanderDistance()) }

    /** CD-SOU-09: one query of the work area, at most once every 20 ticks. An orb gone or moved out since then is skipped. */
    private fun orbs(ctx: WorkContext, area: WorkArea): List<ExperienceOrb> {
        val memo = memo(ctx)
        if (memo.clock.isDue(ctx.now)) {
            memo.found = ctx.level.getEntitiesOfClass(ExperienceOrb::class.java, area.box()) { inArea(it, area) }
            memo.clock.ran(ctx.now)
        }
        return memo.found.filter { inArea(it, area) }
    }

    private fun inArea(orb: ExperienceOrb, area: WorkArea): Boolean = orb.isAlive && area.contains(orb.blockPosition())

    /** The players of the worker's level, with the spectators, whom [OrbRules.nearestPlayer] leaves out. */
    private fun players(ctx: WorkContext): List<ServerPlayer> = ctx.level.players()

    private fun facts(ctx: WorkContext, orb: ExperienceOrb, players: List<ServerPlayer>): OrbFacts {
        val nearest = OrbRules.nearestPlayer(players.map { NearbyPlayer(orb.distanceTo(it).toDouble(), it.isSpectator) })
        return OrbFacts(orb.id, orb.distanceTo(ctx.worker).toDouble(), orb.value, orb.count, nearest)
    }
}
