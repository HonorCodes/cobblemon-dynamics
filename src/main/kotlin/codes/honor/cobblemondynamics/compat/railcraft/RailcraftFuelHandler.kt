package codes.honor.cobblemondynamics.compat.railcraft

import codes.honor.cobblemondynamics.compat.railcraft.RailcraftFuelRule.Slot
import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import mods.railcraft.world.item.RailcraftItems
import mods.railcraft.world.item.crafting.BlastFurnaceRecipe
import mods.railcraft.world.item.crafting.RailcraftRecipeTypes
import mods.railcraft.world.level.block.entity.BlastFurnaceBlockEntity
import mods.railcraft.world.module.BlastFurnaceModule
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.RecipeManager
import net.minecraft.world.item.crafting.SingleRecipeInput
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * The Stoker on the blast furnace of Railcraft Reborn, from level 50 (CD-FUEL-04). It writes the burn time through the public setters
 * of the furnace's module, with no mixin. [RailcraftFireboxHandler] works the solid-fueled boiler, in a role file of its own.
 */
object RailcraftFuelHandler : TargetHandler {
    override val id: ResourceLocation = id("railcraft_fuel")
    override val scanKind: ScanKind = ScanKind.BLOCK_ENTITY

    /**
     * CD-FUEL-04: an idle worker claims a target only while it has demand for that worker, so a worker below the level of the blast
     * furnace never holds one that a stronger worker could fuel.
     */
    override val releasesWhenDone: Boolean = true

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        masterOf(blockEntity) != null

    override fun canonical(level: ServerLevel, pos: BlockPos): BlockPos =
        RailcraftFuelRule.canonical(pos, masterOf(level.getBlockEntity(pos))?.blockPos)

    override fun isPartOfTarget(level: ServerLevel, canonical: BlockPos, pos: BlockPos): Boolean =
        pos == canonical || masterOf(level.getBlockEntity(pos))?.blockPos == canonical

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean =
        masterOf(level.getBlockEntity(canonical))?.blockPos == canonical

    override fun hasDemand(ctx: WorkContext): Boolean {
        val module = furnaceModule(ctx.level.getBlockEntity(ctx.target)) ?: return false
        if (!RailcraftFuelRule.admits(ctx.pokemon.level, RailcraftFuelParams.of(ctx.role.params).blastFurnaceMinLevel)) return false
        return recipeFits(ctx, module)
    }

    override fun applyWork(ctx: WorkContext) {
        if (!hasDemand(ctx)) return
        furnaceModule(ctx.level.getBlockEntity(ctx.target))?.let(::fuelFurnace)
    }

    /** CD-FUEL-04: the furnace's master tick sets its lit state from the burn time. The gauge reads full. */
    private fun fuelFurnace(module: BlastFurnaceModule) {
        val burn = RailcraftFuelRule.furnaceBurn(module.getBurnTime())
        if (burn == module.getBurnTime()) return
        module.setBurnTime(burn)
        module.setCurrentItemBurnTime(burn)
    }

    /** The master of the blast furnace that holds [blockEntity], or null while the furnace is not formed. */
    private fun masterOf(blockEntity: BlockEntity?): BlastFurnaceBlockEntity? =
        (blockEntity as? BlastFurnaceBlockEntity)?.getMasterBlockEntity()?.orElse(null)

    private fun furnaceModule(blockEntity: BlockEntity?): BlastFurnaceModule? = masterOf(blockEntity)?.getBlastFurnaceModule()

    /** Railcraft's own requirement, a blasting recipe for the input, and room for the result and the slag. */
    private fun recipeFits(ctx: WorkContext, module: BlastFurnaceModule): Boolean {
        val input = module.getItem(BlastFurnaceModule.SLOT_INPUT)
        if (input.isEmpty) return false
        val recipe = recipeOf(ctx, input) ?: return false
        val result = recipe.getResultItem(ctx.level.registryAccess())
        val slag = ItemStack(RailcraftItems.SLAG.get(), recipe.slagOutput)
        val slots = listOf(BlastFurnaceModule.SLOT_OUTPUT to result, BlastFurnaceModule.SLOT_SLAG to slag)
            .filter { (_, adding) -> !adding.isEmpty }
            .map { (slot, adding) ->
                val held = module.getItem(slot)
                Slot(held.isEmpty, ItemStack.isSameItemSameComponents(held, adding), held.count, adding.count, held.maxStackSize)
            }
        return RailcraftFuelRule.fits(slots)
    }

    /**
     * A claim's last recipe lookup: the recipe manager and the input item that it read, and the blasting recipe or null. A data pack
     * reload replaces the recipe manager, so a lookup from before it never answers.
     */
    private class RecipeMemo(val recipes: RecipeManager, val input: ItemStack, val recipe: BlastFurnaceRecipe?)

    /** The blasting recipe of [input], read again only when the input item changes, so a Working tick searches no recipe. */
    private fun recipeOf(ctx: WorkContext, input: ItemStack): BlastFurnaceRecipe? {
        val recipes = ctx.level.recipeManager
        (ctx.memo.value as? RecipeMemo)
            ?.takeIf { it.recipes === recipes && ItemStack.isSameItemSameComponents(it.input, input) }
            ?.let { return it.recipe }
        val recipe = recipes.getRecipeFor(RailcraftRecipeTypes.BLASTING.get(), SingleRecipeInput(input), ctx.level)
            .map { it.value() }.orElse(null)
        ctx.memo.value = RecipeMemo(recipes, input.copyWithCount(1), recipe)
        return recipe
    }
}
