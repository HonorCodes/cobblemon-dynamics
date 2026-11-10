package codes.honor.cobblemondynamics.handler.vanilla

import codes.honor.cobblemondynamics.mixin.vanilla.EnchantmentMenuAccess
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.EnchantmentMenu
import net.minecraft.world.level.Level
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** The enchanting menus that players have open, by table, from NeoForge's container events (CD-MUS-05). */
object EnchantingMenus {
    private class Open(val player: Player, val menu: EnchantmentMenu, val dimension: ResourceKey<Level>, val table: BlockPos)

    /** One open menu per player, as a player has one. */
    private val open = ConcurrentHashMap<UUID, Open>()

    fun register() {
        NeoForge.EVENT_BUS.addListener<PlayerContainerEvent.Open> { onOpen(it.entity, it.container) }
        NeoForge.EVENT_BUS.addListener<PlayerContainerEvent.Close> { onClose(it.entity, it.container) }
        NeoForge.EVENT_BUS.addListener<PlayerEvent.PlayerLoggedOutEvent> { open.remove(it.entity.uuid) }
        NeoForge.EVENT_BUS.addListener<ServerStoppedEvent> { open.clear() }
    }

    private fun onOpen(player: Player, menu: AbstractContainerMenu) {
        if (player.level().isClientSide || menu !is EnchantmentMenu) return
        // Mixin adds the accessor interface to EnchantmentMenu.
        val access = (menu as EnchantmentMenuAccess).`cobblemon_dynamics$access`()
        val table = access.evaluate { level, pos -> Open(player, menu, level.dimension(), pos.immutable()) }.orElse(null) ?: return
        open[player.uuid] = table
    }

    private fun onClose(player: Player, menu: AbstractContainerMenu) {
        open.computeIfPresent(player.uuid) { _, entry -> entry.takeUnless { it.menu === menu } }
    }

    /** The menus of [table] that are open now. An entry whose player left or opened another screen goes, should its event be missed. */
    fun menusAt(level: ServerLevel, table: BlockPos): List<EnchantmentMenu> {
        val found = ArrayList<EnchantmentMenu>(1)
        for (entry in open.values) {
            if (entry.player.isRemoved || entry.player.containerMenu !== entry.menu) {
                open.remove(entry.player.uuid, entry)
            } else if (entry.dimension == level.dimension() && entry.table == table) {
                found += entry.menu
            }
        }
        return found
    }

    fun isOpenAt(level: ServerLevel, table: BlockPos): Boolean = menusAt(level, table).isNotEmpty()

    /**
     * Each open menu of [table] computes its offers again, as vanilla does when the item in it changes. The seed stays, so the offers
     * change only with the count of bookshelves. A table whose chunk is gone has no open menu, and is never read.
     */
    fun refresh(level: ServerLevel, table: BlockPos) {
        if (!level.isLoaded(table)) return
        for (menu in menusAt(level, table)) menu.slotsChanged(menu.getSlot(ENCHANT_SLOT).container)
    }

    private const val ENCHANT_SLOT = 0
}
