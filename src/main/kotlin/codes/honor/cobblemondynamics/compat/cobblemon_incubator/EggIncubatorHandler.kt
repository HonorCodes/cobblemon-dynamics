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
import net.minecraft.world.item.ItemStack
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
    private val ANALYSE_UPGRADE: ResourceLocation = ResourceLocation.fromNamespaceAndPath("cobblemon_incubator", "analyse_upgrade")

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        blockEntity is EggIncubatorBlockEntity

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = level.getBlockEntity(canonical) is EggIncubatorBlockEntity

    /** CD-FUEL-11, R88: only where the worker's speed reaches an egg that the incubator's own tick counts down. */
    override fun hasDemand(ctx: WorkContext): Boolean {
        val incubator = incubatorAt(ctx) ?: return false
        val egg = incubator.getItem(EggIncubatorBlockEntity.SLOT_INPUT)
        return IncubatorRule.hasDemand(
            hasEgg = CobbreedingCompat.isEgg(egg),
            upgradesEnabled = IncubatorConfig.speedUpgradeEnabled,
            hasSpeedUpgrade = holds(incubator, SPEED_UPGRADE),
        ) { incubates(incubator, egg) }
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

    /**
     * The incubator's own checks before it counts [egg] down: its filter, which the config can ignore on a shiny egg, and an Analyse
     * Upgrade, which the config can turn off. The filter reads the egg's properties only when it has criteria, as the incubator does.
     */
    private fun incubates(incubator: EggIncubatorBlockEntity, egg: ItemStack): Boolean {
        val filter = incubator.filterConfig()?.takeIf { it.hasCriteria() }
        val properties = filter?.let { CobbreedingCompat.extractProperties(egg) }
        val matches = if (filter != null && properties != null) filter.matches(properties) else null
        return IncubatorRule.incubates(
            analysing = IncubatorConfig.analyseUpgradeEnabled && holds(incubator, ANALYSE_UPGRADE),
            passesFilter = IncubatorRule.passesFilter(criteria = filter != null, matches = matches),
            shiny = properties?.shiny == true,
            ignoreFilterOnShiny = IncubatorConfig.ignoreFilterOnShiny,
        )
    }

    /** The incubator's own private `hasUpgrade`: the item [upgrade] in one of its upgrade slots. */
    private fun holds(incubator: EggIncubatorBlockEntity, upgrade: ResourceLocation): Boolean {
        val item = BuiltInRegistries.ITEM.getOptional(upgrade).orElse(null) ?: return false
        val slots = EggIncubatorBlockEntity.SLOT_UPGRADE_START..EggIncubatorBlockEntity.SLOT_UPGRADE_END
        return slots.any { incubator.getItem(it).`is`(item) }
    }
}
