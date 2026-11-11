package codes.honor.cobblemondynamics.cell

import codes.honor.cobblemondynamics.registry.DynamicsBlocks
import com.mojang.serialization.MapCodec
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState

/**
 * The Charge Cell: a block that stores FE behind NeoForge's energy capability on all six faces (CD-GEN-08) and pushes it into the
 * blocks around it every tick (CD-GEN-09). A Generator worker charges it like any block that takes FE (CD-GEN-01).
 */
class ChargeCellBlock(properties: BlockBehaviour.Properties) : BaseEntityBlock(properties) {
    override fun codec(): MapCodec<ChargeCellBlock> = CODEC

    /** `BaseEntityBlock` renders nothing unless a block says otherwise. */
    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = ChargeCellBlockEntity(pos, state)

    override fun <T : BlockEntity> getTicker(level: Level, state: BlockState, type: BlockEntityType<T>): BlockEntityTicker<T>? =
        if (level.isClientSide) null else createTickerHelper(type, DynamicsBlocks.CHARGE_CELL_TYPE.get(), SERVER_TICK)

    /** CD-GEN-10. */
    override fun hasAnalogOutputSignal(state: BlockState): Boolean = true

    override fun getAnalogOutputSignal(state: BlockState, level: Level, pos: BlockPos): Int =
        (level.getBlockEntity(pos) as? ChargeCellBlockEntity)?.signal ?: 0

    /** CD-GEN-11: the FE that the item carries, also for a new cell, which carries none. */
    override fun appendHoverText(stack: ItemStack, context: Item.TooltipContext, tooltip: MutableList<Component>, flag: TooltipFlag) {
        super.appendHoverText(stack, context, tooltip, flag)
        val stored = stack.getOrDefault(DynamicsBlocks.STORED_ENERGY.get(), 0)
        val line = Component.translatable(ENERGY_KEY, ChargeCellRules.formatFe(stored), ChargeCellRules.formatFe(ChargeCellRules.CAPACITY))
        tooltip += line.withStyle(ChatFormatting.GRAY)
    }

    companion object {
        const val ENERGY_KEY = "cobblemon_dynamics.charge_cell.energy"

        val CODEC: MapCodec<ChargeCellBlock> = simpleCodec(::ChargeCellBlock)

        private val SERVER_TICK = BlockEntityTicker<ChargeCellBlockEntity> { level, _, _, cell -> cell.serverTick(level as ServerLevel) }
    }
}
