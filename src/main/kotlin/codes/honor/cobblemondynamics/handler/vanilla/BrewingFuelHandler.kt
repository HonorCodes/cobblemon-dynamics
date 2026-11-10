package codes.honor.cobblemondynamics.handler.vanilla

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity
import net.minecraft.world.level.block.state.BlockState

/** The Brew Stoker's target: a brewing stand, which the worker fuels as blaze powder does while it can brew (CD-BRF-01). */
object BrewingFuelHandler : TargetHandler {
    private const val BOTTLE_SLOTS = 3
    private const val INGREDIENT_SLOT = 3

    override val id: ResourceLocation = id("brewing_fuel")
    override val scanKind: ScanKind = ScanKind.BLOCK_ENTITY

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        blockEntity is BrewingStandBlockEntity

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = level.getBlockEntity(canonical) is BrewingStandBlockEntity

    /** CD-BRF-04: only while the ingredient can brew one of the bottles. */
    override fun hasDemand(ctx: WorkContext): Boolean = standAt(ctx)?.let { canBrew(ctx.level, it) } ?: false

    /** The memo holds the stand's fuel when this stretch of work starts, which the stop lowers the fuel back to (CD-BRF-05). */
    override fun onStart(ctx: WorkContext) {
        standAt(ctx)?.let { ctx.memo.value = it.fuel }
    }

    override fun applyWork(ctx: WorkContext) {
        val stand = standAt(ctx) ?: return
        setFuel(stand, BrewingRules.fuelWhileWorking(stand.fuel, canBrew(ctx.level, stand)))
    }

    /** A chunk that is gone is never read, because that would load it. */
    override fun onStop(ctx: WorkContext) {
        if (!ctx.level.isLoaded(ctx.target)) return
        val stand = standAt(ctx) ?: return
        val atStart = ctx.memo.value as? Int ?: return
        setFuel(stand, BrewingRules.fuelAfterStop(stand.fuel, atStart))
    }

    private fun standAt(ctx: WorkContext): BrewingStandBlockEntity? = ctx.level.getBlockEntity(ctx.target) as? BrewingStandBlockEntity

    private fun setFuel(stand: BrewingStandBlockEntity, fuel: Int) {
        if (fuel == stand.fuel) return
        stand.fuel = fuel
        stand.setChanged()
    }

    /** Vanilla's private `isBrewable`, as NeoForge 21.1 compiles it: an ingredient that brews at least one of the three bottles. */
    private fun canBrew(level: Level, stand: BrewingStandBlockEntity): Boolean {
        val ingredient = stand.getItem(INGREDIENT_SLOT)
        if (ingredient.isEmpty) return false
        val brewing = level.potionBrewing()
        if (!brewing.isIngredient(ingredient)) return false
        return (0 until BOTTLE_SLOTS).any { slot ->
            val bottle = stand.getItem(slot)
            !bottle.isEmpty && brewing.hasMix(bottle, ingredient)
        }
    }
}
