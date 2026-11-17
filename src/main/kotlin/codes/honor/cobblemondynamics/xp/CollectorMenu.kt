package codes.honor.cobblemondynamics.xp

import codes.honor.cobblemondynamics.registry.DynamicsBlocks
import codes.honor.cobblemondynamics.registry.DynamicsMenus
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerData
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.inventory.SimpleContainerData
import net.minecraft.world.item.ItemStack

/**
 * The menu of an Experience Collector (CD-XPC-04): no slots, and two data slots that sync the stored XP to the screen (CD-XPC-06).
 * Its one button gives all stored XP to the player who presses it (CD-XPC-05).
 */
class CollectorMenu private constructor(
    containerId: Int,
    private val data: ContainerData,
    private val collector: ExperienceCollectorBlockEntity?,
    private val access: ContainerLevelAccess,
) : AbstractContainerMenu(DynamicsMenus.EXPERIENCE_COLLECTOR.get(), containerId) {
    /** The client's menu, whose data slots the server fills. */
    @Suppress("UNUSED_PARAMETER")
    constructor(containerId: Int, inventory: Inventory) :
        this(containerId, SimpleContainerData(CollectorSlots.COUNT), null, ContainerLevelAccess.NULL)

    init {
        checkContainerDataCount(data, CollectorSlots.COUNT)
        addDataSlots(data)
    }

    /** The stored XP of the last sync. */
    val storedXp: Int get() = CollectorSlots.join(data.get(0), data.get(1))

    /**
     * CD-XPC-05: vanilla's packet handler calls this only for the open menu of a player who is no spectator and who still passes
     * [stillValid], so a player out of reach gets nothing.
     */
    override fun clickMenuButton(player: Player, id: Int): Boolean {
        val collector = collector ?: return false
        if (id != CollectorSlots.COLLECT_BUTTON) return false
        if (CollectorSlots.press(id, collector.store, player::giveExperiencePoints) > 0) collector.setChanged()
        return true
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack = ItemStack.EMPTY

    /** The collector is still there, and the player within vanilla's reach of a container. */
    override fun stillValid(player: Player): Boolean = stillValid(access, player, DynamicsBlocks.EXPERIENCE_COLLECTOR.get())

    companion object {
        /** The server's menu over [collector], whose block [access] names. */
        fun server(containerId: Int, collector: ExperienceCollectorBlockEntity, access: ContainerLevelAccess): CollectorMenu =
            CollectorMenu(containerId, CollectorSlots.of(collector.store), collector, access)
    }
}

/** The rules of the menu with no game type, so the unit tests cover them (CD-XPC-05, CD-XPC-06). */
object CollectorSlots {
    /** CD-XPC-06: `ClientboundContainerSetDataPacket` writes each value as a short, so one slot holds 16 bits (RES13). */
    const val COUNT = 2

    const val COLLECT_BUTTON = 0

    /** The low 16 bits of [xp] for slot 0 and the high 16 bits for slot 1. */
    fun part(xp: Int, slot: Int): Int = if (slot == 0) xp and 0xFFFF else (xp ushr 16) and 0xFFFF

    /** The XP of the two slots. The client reads each as a signed short, so only their low 16 bits count. */
    fun join(low: Int, high: Int): Int = ((high and 0xFFFF) shl 16) or (low and 0xFFFF)

    /** CD-XPC-05: the button empties [store] and gives its XP to [give] in the same call. Returns the XP given. */
    fun press(button: Int, store: CollectorStore, give: (Int) -> Unit): Int {
        if (button != COLLECT_BUTTON) return 0
        val xp = store.extract(store.stored, simulate = false)
        if (xp > 0) give(xp)
        return xp
    }

    /** The server's data slots, read from [store] at each sync. */
    fun of(store: CollectorStore): ContainerData = object : ContainerData {
        override fun get(index: Int): Int = part(store.stored, index)

        override fun set(index: Int, value: Int) {}

        override fun getCount(): Int = COUNT
    }
}
