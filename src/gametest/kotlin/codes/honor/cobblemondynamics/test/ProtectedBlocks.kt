package codes.honor.cobblemondynamics.test

import net.minecraft.core.BlockPos
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.common.util.TriState
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent

/**
 * Blocks that a test listener guards as a protection mod does: it cancels every right-click on a block in [cancelled], and denies the
 * held item's use on a block in [itemUseDenied]. Tests add their own absolute positions and remove them after the test.
 */
object ProtectedBlocks {
    val cancelled: MutableSet<BlockPos> = HashSet()
    val itemUseDenied: MutableSet<BlockPos> = HashSet()

    fun register() {
        NeoForge.EVENT_BUS.addListener<PlayerInteractEvent.RightClickBlock> { event ->
            if (event.pos in cancelled) event.setCanceled(true)
            if (event.pos in itemUseDenied) event.useItem = TriState.FALSE
        }
    }
}
