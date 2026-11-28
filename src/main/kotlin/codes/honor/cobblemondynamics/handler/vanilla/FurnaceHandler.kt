package codes.honor.cobblemondynamics.handler.vanilla

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import com.cobblemon.mod.common.CobblemonRecipeTypes
import com.cobblemon.mod.common.block.campfirepot.CampfireBlock
import com.cobblemon.mod.common.block.entity.CampfireBlockEntity
import com.cobblemon.mod.common.item.crafting.CookingPotRecipeBase
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.TagKey
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.item.crafting.SingleRecipeInput
import net.minecraft.world.level.block.AbstractFurnaceBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import kotlin.math.floor

object FurnaceHandler : TargetHandler {
    private const val SLOT_INPUT = 0
    private const val SLOT_RESULT = 2
    private const val HEAT_TICKS = 2
    private const val POT_GRID_START = 1
    private const val POT_GRID_END_EXCLUSIVE = 10
    private const val COOK_BOOST_MIN_MULTIPLIER = 1.5

    override val id: ResourceLocation = id("furnace")
    override val scanKind: ScanKind = ScanKind.BLOCK_ENTITY

    private val NEVER: TagKey<Block> = TagKey.create(Registries.BLOCK, id("stoker/never"))

    fun isTarget(furnace: Boolean, never: Boolean, campfirePot: Boolean = false): Boolean = (furnace || campfirePot) && !never

    fun bonusCookTicks(multiplier: Double): Int =
        if (multiplier >= COOK_BOOST_MIN_MULTIPLIER) floor((multiplier - 1.0) * 2.0).toInt() else 0

    fun boostedProgress(cookingProgress: Int, cookingTotalTime: Int, multiplier: Double): Int {
        val bonus = bonusCookTicks(multiplier)
        if (bonus <= 0 || cookingTotalTime <= 1 || cookingProgress < 0 || cookingProgress >= cookingTotalTime - 1) {
            return cookingProgress
        }
        return minOf(cookingProgress + bonus, cookingTotalTime - 1)
    }

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        isTarget(blockEntity is AbstractFurnaceBlockEntity, state.`is`(NEVER), isCampfirePot(state, blockEntity))

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean {
        val state = level.getBlockState(canonical)
        val blockEntity = level.getBlockEntity(canonical)
        return isTarget(blockEntity is AbstractFurnaceBlockEntity, state.`is`(NEVER), isCampfirePot(state, blockEntity))
    }

    override fun hasDemand(ctx: WorkContext): Boolean {
        val state = ctx.level.getBlockState(ctx.target)
        if (state.`is`(NEVER)) return false
        return when (val blockEntity = ctx.level.getBlockEntity(ctx.target)) {
            is AbstractFurnaceBlockEntity -> canCook(ctx.level, blockEntity)
            is CampfireBlockEntity -> state.block is CampfireBlock && potHasDemand(ctx.level, state, blockEntity)
            else -> false
        }
    }

    override fun applyWork(ctx: WorkContext) {
        val state = ctx.level.getBlockState(ctx.target)
        if (state.`is`(NEVER)) return
        when (val blockEntity = ctx.level.getBlockEntity(ctx.target)) {
            is AbstractFurnaceBlockEntity -> stokeFurnace(ctx, state, blockEntity)
            is CampfireBlockEntity -> if (state.block is CampfireBlock) stokePot(ctx, state, blockEntity)
        }
    }

    private fun isCampfirePot(state: BlockState, blockEntity: BlockEntity?): Boolean =
        state.block is CampfireBlock && blockEntity is CampfireBlockEntity

    private fun stokeFurnace(ctx: WorkContext, state: BlockState, furnace: AbstractFurnaceBlockEntity) {
        if (!canCook(ctx.level, furnace)) return
        if (furnace.litTime < HEAT_TICKS) {
            furnace.litTime = HEAT_TICKS
            furnace.litDuration = HEAT_TICKS
        }
        furnace.cookingProgress = boostedProgress(furnace.cookingProgress, furnace.cookingTotalTime, ctx.multiplier)
        if (state.hasProperty(AbstractFurnaceBlock.LIT) && !state.getValue(AbstractFurnaceBlock.LIT)) {
            ctx.level.setBlock(ctx.target, state.setValue(AbstractFurnaceBlock.LIT, true), Block.UPDATE_ALL)
        }
        furnace.setChanged()
    }

    private fun potHasDemand(level: ServerLevel, state: BlockState, pot: CampfireBlockEntity): Boolean {
        val data = pot.dataAccess
        val progress = data.get(CampfireBlockEntity.COOKING_PROGRESS_INDEX)
        val totalTime = data.get(CampfireBlockEntity.COOKING_PROGRESS_TOTAL_TIME_INDEX)
        val lidOpen = data.get(CampfireBlockEntity.IS_LID_OPEN_INDEX)
        val cooking = state.getOptionalValue(CampfireBlock.COOKING).orElse(false)
        val canStart = lidOpen == 0 && progress == 0 && totalTime > 0 && canStartPotRecipe(level, pot)
        return CampfirePotRule.hasDemand(
            progress = progress,
            totalTime = totalTime,
            lidOpen = lidOpen,
            cooking = cooking,
            never = false,
            canStartRecipe = canStart,
        )
    }

    private fun canStartPotRecipe(level: ServerLevel, pot: CampfireBlockEntity): Boolean {
        val grid = pot.items.subList(POT_GRID_START, POT_GRID_END_EXCLUSIVE)
        if (grid.all { it.isEmpty }) return false
        val width = CampfireBlockEntity.CRAFTING_GRID_WIDTH
        val input = CraftingInput.of(width, width, grid)
        val manager = level.recipeManager
        val recipe: CookingPotRecipeBase = manager.getRecipeFor(CobblemonRecipeTypes.COOKING_POT_COOKING, input, level)
            .map { it.value() as CookingPotRecipeBase }
            .orElseGet {
                manager.getRecipeFor(CobblemonRecipeTypes.COOKING_POT_SHAPELESS, input, level)
                    .map { it.value() as CookingPotRecipeBase }
                    .orElse(null)
            } ?: return false
        val result = recipe.assemble(input, level.registryAccess())
        if (result.isEmpty) return false
        val seasonings = pot.getSeasonings().filter { it.`is`(recipe.seasoningTag) }
        recipe.applySeasoning(result, seasonings)
        val current = pot.getItem(CampfireBlockEntity.RESULT_SLOT)
        return CampfirePotRule.canAcceptOutput(
            currentEmpty = current.isEmpty,
            sameItemAndComponents = !current.isEmpty && ItemStack.isSameItemSameComponents(current, result),
            currentCount = current.count,
            resultCount = result.count,
            maxStackSize = minOf(current.maxStackSize, result.maxStackSize),
        )
    }

    private fun stokePot(ctx: WorkContext, state: BlockState, pot: CampfireBlockEntity) {
        if (!potHasDemand(ctx.level, state, pot)) return
        if (state.hasProperty(BlockStateProperties.LIT) && !state.getValue(BlockStateProperties.LIT)) {
            ctx.level.setBlock(ctx.target, state.setValue(BlockStateProperties.LIT, true), Block.UPDATE_ALL)
        }
        val data = pot.dataAccess
        val progress = data.get(CampfireBlockEntity.COOKING_PROGRESS_INDEX)
        val totalTime = data.get(CampfireBlockEntity.COOKING_PROGRESS_TOTAL_TIME_INDEX)
        val next = CampfirePotRule.nextProgress(progress, totalTime, CampfireBlockEntity.COOKING_PROGRESS_PER_TICK)
        if (next != progress) {
            data.set(CampfireBlockEntity.COOKING_PROGRESS_INDEX, next)
            pot.setChanged()
        }
    }

    private fun canCook(level: ServerLevel, furnace: AbstractFurnaceBlockEntity): Boolean {
        val input = furnace.getItem(SLOT_INPUT)
        if (input.isEmpty) return false
        val recipeInput = SingleRecipeInput(input)
        val result = furnace.quickCheck.getRecipeFor(recipeInput, level)
            .map { it.value().assemble(recipeInput, level.registryAccess()) }
            .orElse(ItemStack.EMPTY)
        if (result.isEmpty) return false
        val output = furnace.getItem(SLOT_RESULT)
        if (output.isEmpty) return true
        if (!ItemStack.isSameItemSameComponents(output, result)) return false
        val total = output.count + result.count
        return (total <= furnace.maxStackSize && total <= output.maxStackSize) || total <= result.maxStackSize
    }
}
