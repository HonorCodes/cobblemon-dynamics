package codes.honor.cobblemondynamics.compat.ironfurnaces

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import ironfurnaces.tileentity.furnaces.BlockIronFurnaceTileBase
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.AbstractCookingRecipe
import net.minecraft.world.item.crafting.RecipeManager
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.item.crafting.SingleRecipeInput
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import java.util.WeakHashMap

private typealias CookingCheck = RecipeManager.CachedCheck<SingleRecipeInput, AbstractCookingRecipe>

/**
 * The Stoker's target in Iron Furnaces: every furnace whose block entity extends `BlockIronFurnaceTileBase`, which no vanilla furnace
 * class covers (CD-FUEL-07). The worker keeps the public `furnaceBurnTime` up while the furnace's own recipe check passes, so each tier
 * smelts at its own speed with no cap (ruling 3 of R80), and its fuel slot waits (CD-FUEL-13).
 */
object IronFurnaceHandler : TargetHandler {
    override val id: ResourceLocation = id("iron_furnace")
    override val scanKind: ScanKind = ScanKind.BLOCK_ENTITY

    // One recipe lookup per furnace, keyed by its block entity, which a replaced block or an unloaded chunk lets go. Server thread only.
    private val checks = WeakHashMap<BlockEntity, FurnaceCheck<RecipeType<AbstractCookingRecipe>, CookingCheck>>()

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        blockEntity is BlockIronFurnaceTileBase

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = level.getBlockEntity(canonical) is BlockIronFurnaceTileBase

    override fun hasDemand(ctx: WorkContext): Boolean =
        furnaceAt(ctx)?.let { IronFurnaceRule.hasDemand(facts(ctx.level, it, ctx.now)) } ?: false

    /**
     * The furnace writes its `LIT` state only when its own burning starts or ends, so the worker lights a furnace that it starts, as the
     * vanilla Stoker does (CD-STK-04). The furnace's own tick puts it out once the burn time runs out.
     */
    override fun applyWork(ctx: WorkContext) {
        val furnace = furnaceAt(ctx) ?: return
        val facts = facts(ctx.level, furnace, ctx.now)
        val burnTime = IronFurnaceRule.burnTime(facts)
        if (burnTime == facts.burnTime) return
        furnace.furnaceBurnTime = burnTime
        val state = ctx.level.getBlockState(ctx.target)
        if (state.hasProperty(BlockStateProperties.LIT) && !state.getValue(BlockStateProperties.LIT)) {
            ctx.level.setBlock(ctx.target, state.setValue(BlockStateProperties.LIT, true), Block.UPDATE_ALL)
        }
        furnace.setChanged()
    }

    private fun furnaceAt(ctx: WorkContext): BlockIronFurnaceTileBase? = ctx.level.getBlockEntity(ctx.target) as? BlockIronFurnaceTileBase

    /** The demand and the work of one tick share the furnace's recipe lookup, which runs once in that tick [now]. */
    private fun facts(level: ServerLevel, furnace: BlockIronFurnaceTileBase, now: Long): IronFurnaceFacts {
        @Suppress("UNCHECKED_CAST")
        val type = furnace.recipeType as RecipeType<AbstractCookingRecipe>
        val check = FurnaceCheck.forType(checks[furnace], type) { RecipeManager.createCheck(it) }.also { checks[furnace] = it }
        return IronFurnaceFacts(
            furnaceMode = furnace.isFurnace,
            canSmelt = check.canSmelt(now) { canSmelt(level, furnace, it) },
            burnTime = furnace.furnaceBurnTime,
        )
    }

    /** The furnace's own protected `canSmelt` over the recipe of its current recipe type, which an augment turns to smoking or blasting. */
    private fun canSmelt(level: ServerLevel, furnace: BlockIronFurnaceTileBase, check: CookingCheck): Boolean {
        val input = furnace.getItem(BlockIronFurnaceTileBase.INPUT)
        if (input.isEmpty) return false
        val recipe = check.getRecipeFor(SingleRecipeInput(input), level).orElse(null) ?: return false
        val result = recipe.value().getResultItem(level.registryAccess())
        if (result.isEmpty) return false
        val output = furnace.getItem(BlockIronFurnaceTileBase.OUTPUT)
        return IronFurnaceRule.hasRoom(
            outputEmpty = output.isEmpty,
            sameItem = ItemStack.isSameItemSameComponents(output, result),
            outputCount = output.count,
            resultCount = result.count,
            outputMaxStack = output.maxStackSize,
        )
    }
}
