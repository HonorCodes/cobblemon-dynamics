package codes.honor.cobblemondynamics.compat.botanypots

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.handler.farming.CultivatorParams
import codes.honor.cobblemondynamics.handler.farming.GrowthPace
import codes.honor.cobblemondynamics.handler.vanilla.TickClock
import codes.honor.cobblemondynamics.id
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * The Cultivator on Botany Pots: a Ground-type gives a pot the growth of one bone meal on each use of its pace, with no item, for
 * Work Stamina, and silently (CD-FRM-04). A pot is no `BonemealableBlock`, so the crop Cultivator never matches it. The pot claims in
 * the Cultivator's lane, so it takes one Cultivator beside its Harvester (CD-FRM-06).
 */
object PotCultivatorHandler : TargetHandler {
    /** A use that the land claim checks refused waits as long as a refused harvest (CD-HRV-13). */
    private const val REFUSED_WAIT_TICKS = 6_000L

    override val id: ResourceLocation = id("pot_cultivator")
    override val scanKind: ScanKind = ScanKind.BLOCK_ENTITY
    override val claimLane: String = PotRules.CULTIVATOR_LANE

    /** CD-CRP-10: an auto-assigned worker goes pot to pot. */
    override val releasesWhenDone: Boolean = true

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        blockEntity != null && PotAdapter.isPot(blockEntity)

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean =
        level.getBlockEntity(canonical)?.let(PotAdapter::isPot) == true

    override fun hasDemand(ctx: WorkContext): Boolean {
        if ((ctx.memo.value as? Memo)?.let { ctx.now < it.refusedUntil } == true) return false
        return potFacts(ctx)?.let(PotRules::cultivatorDemand) ?: false
    }

    /** The uses count from the tick that this stretch of work started. */
    override fun onStart(ctx: WorkContext) {
        val memo = ctx.memo.value as? Memo
        if (memo == null) ctx.memo.value = Memo(ctx.now) else memo.since = ctx.now
    }

    /**
     * CD-FRM-04, CD-FRM-07: one growth use on each use tick of the Cultivator's pace. The use question of the pot comes in the tick of
     * the use, and a refused use grows nothing and waits.
     */
    override fun applyWork(ctx: WorkContext) {
        val memo = ctx.memo.value as? Memo ?: Memo(ctx.now).also { ctx.memo.value = it }
        val params = CultivatorParams.of(ctx.role.params)
        if (!TickClock.every(GrowthPace.intervalTicks(ctx.pokemon.level, params.usesPerMinuteAt100), memo.since, ctx.now)) return
        val pot = ctx.level.getBlockEntity(ctx.target) ?: return
        val facts = PotAdapter.facts(pot) ?: return
        if (!PotRules.cultivatorDemand(facts)) return
        val growth = PotRules.grown(facts, PotRules.roll(ctx.level.random::nextInt)) { ctx.mayChange(ctx.target) }
        if (growth == null) {
            memo.refusedUntil = ctx.now + REFUSED_WAIT_TICKS
            ctx.stalled(REFUSED_WAIT_TICKS)
            return
        }
        PotAdapter.grow(pot, growth, PotRules.BONEMEAL_COOLDOWN)
        ctx.spend(params.staminaTicksPerUse)
        sparkle(ctx.level, ctx.target)
    }

    /** The green of a bone meal use, as the crop Cultivator shows it: a particle packet, with no sound. */
    private fun sparkle(level: ServerLevel, pos: BlockPos) {
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, SPARKLES, 0.25, 0.25, 0.25, 0.0)
    }

    private fun potFacts(ctx: WorkContext): PotFacts? = ctx.level.getBlockEntity(ctx.target)?.let(PotAdapter::facts)

    private class Memo(var since: Long) {
        var refusedUntil = Long.MIN_VALUE
    }

    private const val SPARKLES = 15
}
