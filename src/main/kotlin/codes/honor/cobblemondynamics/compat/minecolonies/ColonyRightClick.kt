package codes.honor.cobblemondynamics.compat.minecolonies

/**
 * The right-click rule of MineColonies 1.1.1419 for an empty main hand, in the order of
 * `ColonyPermissionEventHandler.onPlayerInteract`, as RES7 read it (CD-LND-08). Names no MineColonies class, so a unit test reads it
 * without MineColonies.
 */
object ColonyRightClick {
    /** The permissions of a rank that the rule asks (`Action`). */
    enum class Need { ACCESS_HUTS, ACCESS_TOGGLEABLES, RIGHTCLICK_BLOCK, OPEN_CONTAINER }

    /**
     * The block: a hut of the colony, a block of the tag `colonyProtectionException`, a door or a fence gate, and a block with an item
     * handler.
     */
    data class Block(val hut: Boolean, val exception: Boolean, val toggleable: Boolean, val container: Boolean)

    /** Free blocks of a colony are refused, because the API has no getter for them, and the attackers of `pvp_mode` are not asked. */
    fun allows(block: Block, protection: Boolean, hostile: Boolean, has: (Need) -> Boolean): Boolean = when {
        block.hut && !has(Need.ACCESS_HUTS) -> false
        block.exception && !hostile -> true
        block.toggleable && has(Need.ACCESS_TOGGLEABLES) -> true
        !protection -> true
        !has(Need.RIGHTCLICK_BLOCK) -> false
        else -> !block.container || has(Need.OPEN_CONTAINER)
    }
}
