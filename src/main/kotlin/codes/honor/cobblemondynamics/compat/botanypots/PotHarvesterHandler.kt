package codes.honor.cobblemondynamics.compat.botanypots

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.handler.farming.DropFace
import codes.honor.cobblemondynamics.handler.farming.HarvestClock
import codes.honor.cobblemondynamics.id
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * The Harvester on Botany Pots: a Grass-type harvests a basic pot as a player does, and the harvest drops beside the pot on the side of
 * the worker (CD-FRM-05). A hopper pot harvests itself. A pot harvest breaks no block, so it asks the use question only (CD-FRM-07).
 * The pot claims in the Harvester's lane, so it takes one Harvester beside its Cultivator (CD-FRM-06).
 */
object PotHarvesterHandler : TargetHandler {
    /** A harvest that the land claim checks refused, or that its crop could not roll, waits as long as a refused crop (CD-HRV-13). */
    private const val REFUSED_WAIT_TICKS = 6_000L

    override val id: ResourceLocation = id("pot_harvester")
    override val scanKind: ScanKind = ScanKind.BLOCK_ENTITY
    override val claimLane: String = PotRules.HARVESTER_LANE

    /** CD-CRP-10: an auto-assigned worker goes pot to pot. */
    override val releasesWhenDone: Boolean = true

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        blockEntity != null && PotAdapter.isPot(blockEntity)

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean =
        level.getBlockEntity(canonical)?.let(PotAdapter::isPot) == true

    override fun hasDemand(ctx: WorkContext): Boolean {
        if ((ctx.memo.value as? Memo)?.let { ctx.now < it.refusedUntil } == true) return false
        return ctx.level.getBlockEntity(ctx.target)?.let(PotAdapter::facts)?.let(PotRules::harvesterDemand) ?: false
    }

    /** The harvest counts from the tick that this stretch of work started. */
    override fun onStart(ctx: WorkContext) {
        val memo = ctx.memo.value as? Memo
        if (memo == null) {
            ctx.memo.value = Memo(ctx.now)
        } else {
            memo.since = ctx.now
            memo.harvested = false
        }
    }

    /** CD-FRM-05: one harvest, `harvest_ticks` after the work started, with the use question in the tick of the harvest. */
    override fun applyWork(ctx: WorkContext) {
        val memo = ctx.memo.value as? Memo ?: Memo(ctx.now).also { ctx.memo.value = it }
        val delay = HarvestClock.ticksOf(ctx.role.params, ctx.multiplier)
        if (!HarvestClock.shouldHarvest(memo.since, ctx.now, delay, memo.harvested)) return
        memo.harvested = true
        val pot = ctx.level.getBlockEntity(ctx.target) ?: return
        val facts = PotAdapter.facts(pot) ?: return
        if (!PotRules.harvesterDemand(facts)) return
        val face = DropFace.toward(ctx.target, ctx.spot ?: ctx.target)
        if (!PotRules.harvests(facts) { ctx.mayChange(ctx.target) } || !PotAdapter.harvest(ctx.level, pot, face)) {
            memo.refusedUntil = ctx.now + REFUSED_WAIT_TICKS
            ctx.stalled(REFUSED_WAIT_TICKS)
        }
    }

    private class Memo(var since: Long) {
        var harvested = false
        var refusedUntil = Long.MIN_VALUE
    }
}
