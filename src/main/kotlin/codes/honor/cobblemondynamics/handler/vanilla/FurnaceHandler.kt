package codes.honor.cobblemondynamics.handler.vanilla

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.SingleRecipeInput
import net.minecraft.world.level.block.AbstractFurnaceBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/** The Stoker's target: every furnace, smoker, blast furnace and modded subclass of `AbstractFurnaceBlockEntity` (CD-STK-01). */
object FurnaceHandler : TargetHandler {
    private const val SLOT_INPUT = 0
    private const val SLOT_RESULT = 2

    /**
     * Vanilla takes fuel only when its tick brings `litTime` to 0, and the engine works after the block entities tick. So 2 ticks
     * of heat keep the recipe running and never touch the fuel slot (CD-STK-02, CD-STK-03), and the furnace goes out within
     * 2 ticks once the work stops (CD-STK-06).
     */
    private const val HEAT_TICKS = 2

    override val id: ResourceLocation = id("furnace")
    override val scanKind: ScanKind = ScanKind.BLOCK_ENTITY

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        blockEntity is AbstractFurnaceBlockEntity

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean =
        level.getBlockEntity(canonical) is AbstractFurnaceBlockEntity

    override fun hasDemand(ctx: WorkContext): Boolean = furnaceAt(ctx)?.let { canCook(ctx.level, it) } ?: false

    /**
     * `litDuration = litTime` keeps the flame gauge full while the furnace cooks (CD-STK-07). A furnace that burns its own fuel
     * keeps its own values.
     */
    override fun applyWork(ctx: WorkContext) {
        val furnace = furnaceAt(ctx) ?: return
        if (!canCook(ctx.level, furnace)) return
        if (furnace.litTime < HEAT_TICKS) {
            furnace.litTime = HEAT_TICKS
            furnace.litDuration = HEAT_TICKS
        }
        val state = ctx.level.getBlockState(ctx.target)
        if (state.hasProperty(AbstractFurnaceBlock.LIT) && !state.getValue(AbstractFurnaceBlock.LIT)) {
            ctx.level.setBlock(ctx.target, state.setValue(AbstractFurnaceBlock.LIT, true), Block.UPDATE_ALL)
        }
        furnace.setChanged()
    }

    private fun furnaceAt(ctx: WorkContext): AbstractFurnaceBlockEntity? =
        ctx.level.getBlockEntity(ctx.target) as? AbstractFurnaceBlockEntity

    /** Vanilla's private `canBurn`, with NeoForge's stack-size fix (CD-STK-05). */
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
