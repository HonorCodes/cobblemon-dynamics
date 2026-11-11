package codes.honor.cobblemondynamics.leash

import codes.honor.cobblemondynamics.config.WorkEngineSettings
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level

/** The Work Leash. It selects a Pokémon with an entity click (see [LeashInteractions]) and binds it with a block click. */
class WorkLeashItem(properties: Item.Properties) : Item(properties) {
    /**
     * Runs before the block's own interaction. A sneak-click on a target that a worker drives flips it first (CD-WHL-05). With a
     * selection it always consumes the click, so the block never opens its screen (CD-LSH-04). The client only predicts that from
     * its synced copy of the stack; the server decides the flip and the bind.
     */
    override fun onItemUseFirst(stack: ItemStack, context: UseOnContext): InteractionResult {
        if (context.hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS
        val player = context.player
        if (player is ServerPlayer && player.isShiftKeyDown && LeashInteractions.onSneakBlockClick(player, context.clickedPos)) {
            return InteractionResult.SUCCESS
        }
        if (!stack.has(LeashComponents.SELECTION.get())) return InteractionResult.PASS
        val serverPlayer = player as? ServerPlayer ?: return InteractionResult.SUCCESS
        LeashInteractions.onBlockClick(serverPlayer, stack, context.clickedPos, context.clickedFace)
        return InteractionResult.SUCCESS
    }

    /** CD-LSH-19: a right-click on air clears the pending source at once. */
    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)
        if (!stack.has(LeashComponents.PENDING_SOURCE.get())) return InteractionResultHolder.pass(stack)
        stack.remove(LeashComponents.PENDING_SOURCE.get())
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
    }

    /**
     * CD-LSH-19: a pending source expires `leash.pendingSelectionTicks` after its click. One that the player carried into another
     * level goes on the first tick there, because its position names no block of that level.
     */
    override fun inventoryTick(stack: ItemStack, level: Level, entity: Entity, slotId: Int, isSelected: Boolean) {
        if (level.isClientSide) return
        val pending = stack.get(LeashComponents.PENDING_SOURCE.get()) ?: return
        if (!pending.liveIn(level.dimension().location(), level.gameTime, WorkEngineSettings.current.pendingSelectionTicks)) {
            stack.remove(LeashComponents.PENDING_SOURCE.get())
        }
    }

    override fun appendHoverText(stack: ItemStack, context: Item.TooltipContext, tooltip: MutableList<Component>, flag: TooltipFlag) {
        val selection = stack.get(LeashComponents.SELECTION.get()) ?: return
        tooltip += Component.translatable(SELECTED, selection.name)
    }

    private companion object {
        const val SELECTED = "cobblemon_dynamics.leash.selected"
    }
}
