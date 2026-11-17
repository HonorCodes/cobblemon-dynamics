package codes.honor.cobblemondynamics.xp

import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.registry.DynamicsBlocks
import com.mojang.serialization.MapCodec
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionResult
import net.minecraft.world.MenuProvider
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.DirectionProperty
import net.minecraft.world.phys.BlockHitResult

/**
 * The Experience Collector (CD-XPC-01): a block that stores XP. Its front faces the player who places it (CD-XPC-02), a right-click
 * opens its screen (CD-XPC-04), a comparator reads its fill (CD-XPC-12), and its item keeps the XP of a broken collector (CD-XPC-13).
 */
class ExperienceCollectorBlock(properties: BlockBehaviour.Properties) : BaseEntityBlock(properties) {
    init {
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH))
    }

    override fun codec(): MapCodec<ExperienceCollectorBlock> = CODEC

    /** `BaseEntityBlock` renders nothing unless a block says otherwise. */
    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = ExperienceCollectorBlockEntity(pos, state)

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING)
    }

    /** CD-XPC-02: the front turns toward the player, as a furnace's does, so the back and the bottom outputs face away. */
    override fun getStateForPlacement(context: BlockPlaceContext): BlockState =
        defaultBlockState().setValue(FACING, context.horizontalDirection.opposite)

    override fun rotate(state: BlockState, rotation: Rotation): BlockState = state.setValue(FACING, rotation.rotate(state.getValue(FACING)))

    override fun mirror(state: BlockState, mirror: Mirror): BlockState = state.rotate(mirror.getRotation(state.getValue(FACING)))

    /**
     * CD-XPC-15: the screen opens only here, on the block's own use path, so a claim mod that cancels the right-click keeps the player
     * out, as at a chest. Anyone who reaches this path may collect.
     */
    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult,
    ): InteractionResult {
        if (level.isClientSide) return InteractionResult.SUCCESS
        player.openMenu(state.getMenuProvider(level, pos))
        return InteractionResult.CONSUME
    }

    override fun getMenuProvider(state: BlockState, level: Level, pos: BlockPos): MenuProvider? {
        val collector = level.getBlockEntity(pos) as? ExperienceCollectorBlockEntity ?: return null
        val access = ContainerLevelAccess.create(level, pos)
        return SimpleMenuProvider({ id, _, _ -> CollectorMenu.server(id, collector, access) }, state.block.name)
    }

    /** CD-XPC-12. */
    override fun hasAnalogOutputSignal(state: BlockState): Boolean = true

    override fun getAnalogOutputSignal(state: BlockState, level: Level, pos: BlockPos): Int =
        (level.getBlockEntity(pos) as? ExperienceCollectorBlockEntity)?.signal ?: 0

    /** CD-XPC-13: the XP that the item carries out of the capacity of the server config, also for a new collector, which carries none. */
    override fun appendHoverText(stack: ItemStack, context: Item.TooltipContext, tooltip: MutableList<Component>, flag: TooltipFlag) {
        super.appendHoverText(stack, context, tooltip, flag)
        val stored = stack.getOrDefault(DynamicsBlocks.STORED_EXPERIENCE.get(), 0)
        val capacity = WorkEngineSettings.current.experienceCollectorCapacity
        val line = Component.translatable(STORED_KEY, CollectorStore.format(stored), CollectorStore.format(capacity))
        tooltip += line.withStyle(ChatFormatting.GRAY)
    }

    companion object {
        const val STORED_KEY = "cobblemon_dynamics.experience_collector.stored"

        /** The way the front faces. The back output faces the other way (CD-XPC-07). */
        val FACING: DirectionProperty = BlockStateProperties.HORIZONTAL_FACING

        val CODEC: MapCodec<ExperienceCollectorBlock> = simpleCodec(::ExperienceCollectorBlock)
    }
}
