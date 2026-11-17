package codes.honor.cobblemondynamics.compat.cobblemon_incubator

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import com.nbp.cobblemon_incubator.blockentity.EggIncubatorBlockEntity
import com.nbp.cobblemon_incubator.config.IncubatorConfig
import com.nbp.cobblemon_incubator.util.CobbreedingCompat
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * The Stoker's target in Cobblemon Incubator: the Egg Incubator, whose theme is warmth, as Flame Body hatches eggs (CD-FUEL-11). The
 * worker grants the incubator its warmth for each tick of work, and the incubator's mixin turns that into the speed of one Speed Upgrade.
 */
object EggIncubatorHandler : TargetHandler {
    override val id: ResourceLocation = id("egg_incubator")
    override val scanKind: ScanKind = ScanKind.BLOCK_ENTITY

    private val SPEED_UPGRADE: ResourceLocation = ResourceLocation.fromNamespaceAndPath("cobblemon_incubator", "speed_upgrade")

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        blockEntity is EggIncubatorBlockEntity

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = level.getBlockEntity(canonical) is EggIncubatorBlockEntity

    override fun hasDemand(ctx: WorkContext): Boolean {
        val incubator = incubatorAt(ctx) ?: return false
        return IncubatorRule.hasDemand(
            hasEgg = CobbreedingCompat.isEgg(incubator.getItem(EggIncubatorBlockEntity.SLOT_INPUT)),
            upgradesEnabled = IncubatorConfig.speedUpgradeEnabled,
            hasSpeedUpgrade = hasSpeedUpgrade(incubator),
        )
    }

    override fun applyWork(ctx: WorkContext) {
        incubatorAt(ctx)?.let { IncubatorWarmth.grant(it, ctx.now) }
    }

    /** CD-FUEL-14: the incubator's next tick runs at its own speed. In a chunk that is gone, the grant lapses. */
    override fun onStop(ctx: WorkContext) {
        if (!ctx.level.isLoaded(ctx.target)) return
        incubatorAt(ctx)?.let(IncubatorWarmth::withdraw)
    }

    private fun incubatorAt(ctx: WorkContext): EggIncubatorBlockEntity? = ctx.level.getBlockEntity(ctx.target) as? EggIncubatorBlockEntity

    /** The incubator's own private check: a Speed Upgrade in one of its upgrade slots. */
    private fun hasSpeedUpgrade(incubator: EggIncubatorBlockEntity): Boolean {
        val upgrade = BuiltInRegistries.ITEM.getOptional(SPEED_UPGRADE).orElse(null) ?: return false
        return (EggIncubatorBlockEntity.SLOT_UPGRADE_START..EggIncubatorBlockEntity.SLOT_UPGRADE_END).any {
            incubator.getItem(it).`is`(upgrade)
        }
    }
}
