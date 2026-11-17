package codes.honor.cobblemondynamics.compat.mekanismgenerators

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import mekanism.common.tile.TileEntityBoundingBlock
import mekanism.generators.common.config.MekanismGeneratorsConfig
import mekanism.generators.common.tile.TileEntityWindGenerator
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * Tailwind on the Mekanism Wind Generator (CD-AIR-01). The hook in `mixin/compat/mekanismgenerators` raises the multiplier that
 * Mekanism stores every 20 ticks, so the generator makes more power at its own update, and a stopped worker's bonus ends at the next
 * one (CD-AIR-02, CD-AIR-03).
 */
object WindGeneratorHandler : TargetHandler {
    override val id: ResourceLocation = id("wind_generator")

    /** Leash only (CD-TLW-02). */
    override val scanKind: ScanKind = ScanKind.NONE

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        mainOf(level, blockEntity) != null

    /** CD-AIR-04: a click or a binding on the generator's upper blocks maps to its main block, for the leash and the claim alike. */
    override fun canonical(level: ServerLevel, pos: BlockPos): BlockPos = WindBonus.canonical(pos, mainOf(level, level.getBlockEntity(pos)))

    override fun isPartOfTarget(level: ServerLevel, canonical: BlockPos, pos: BlockPos): Boolean =
        pos == canonical || mainOf(level, level.getBlockEntity(pos)) == canonical

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = level.getBlockEntity(canonical) is TileEntityWindGenerator

    /** Mekanism's own multiplier is above 0, so the generator sees the sky, outside a dimension that Mekanism's config bars. */
    override fun hasDemand(ctx: WorkContext): Boolean {
        val generator = generatorAt(ctx) ?: return false
        return !generator.isBlacklistDimension && generator.currentMultiplier > 0.0
    }

    override fun onStart(ctx: WorkContext) = applyWork(ctx)

    /** CD-AIR-03: renews the grant on every Working tick. Only the server reads it, so it sends nothing. */
    override fun applyWork(ctx: WorkContext) {
        val generator = generatorAt(ctx) ?: return
        val grant = generator.getData(MekanismGeneratorsCompat.WIND_GRANT.get())
        grant.level = ctx.pokemon.level
        grant.until = WindBonus.until(ctx.now)
    }

    override fun onStop(ctx: WorkContext) {
        if (!ctx.level.isLoaded(ctx.target)) return
        val generator = generatorAt(ctx) ?: return
        if (generator.hasData(MekanismGeneratorsCompat.WIND_GRANT.get())) generator.removeData(MekanismGeneratorsCompat.WIND_GRANT.get())
    }

    /** CD-AIR-02: what the hook returns in place of Mekanism's own multiplier [own], with M from Mekanism Generators' config. */
    @JvmStatic
    fun multiplier(generator: BlockEntity, own: Double): Double {
        val grant = generator.getExistingDataOrNull(MekanismGeneratorsCompat.WIND_GRANT.get()) ?: return own
        val now = generator.level?.gameTime ?: return own
        val config = MekanismGeneratorsConfig.generators
        return WindBonus.applied(own, WindBonus.top(config.windGenerationMin.get(), config.windGenerationMax.get()), grant, now)
    }

    private fun generatorAt(ctx: WorkContext): TileEntityWindGenerator? = ctx.level.getBlockEntity(ctx.target) as? TileEntityWindGenerator

    /** The generator's main block, from the generator or from one of Mekanism's bounding blocks above it. */
    private fun mainOf(level: ServerLevel, blockEntity: BlockEntity?): BlockPos? = when (blockEntity) {
        is TileEntityWindGenerator -> blockEntity.blockPos
        is TileEntityBoundingBlock -> blockEntity.mainPos.takeIf { level.getBlockEntity(it) is TileEntityWindGenerator }
        else -> null
    }
}
