package codes.honor.cobblemondynamics.compat

import codes.honor.cobblemondynamics.compat.minecolonies.ColonyRightClick
import codes.honor.cobblemondynamics.compat.minecolonies.ColonyRightClick.Need
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The right-click rule of a colony, in the order of `ColonyPermissionEventHandler.onPlayerInteract` of MineColonies 1.1.1419
 * (CD-LND-08). Names no class of MineColonies.
 */
class ColonyRightClickTest {
    private val plain = ColonyRightClick.Block(hut = false, exception = false, toggleable = false, container = false)
    private val all: (Need) -> Boolean = { true }
    private val none: (Need) -> Boolean = { false }

    private fun only(vararg needs: Need): (Need) -> Boolean = { it in needs }

    private fun allows(block: ColonyRightClick.Block, has: (Need) -> Boolean, protection: Boolean = true, hostile: Boolean = false) =
        ColonyRightClick.allows(block, protection, hostile, has)

    @Test
    fun `CD-LND-08 a hut needs ACCESS_HUTS before every other rule`() {
        val hut = plain.copy(hut = true, exception = true, toggleable = true, container = true)
        assertFalse(allows(hut, only(Need.ACCESS_TOGGLEABLES, Need.RIGHTCLICK_BLOCK, Need.OPEN_CONTAINER)))
        assertFalse(allows(hut, none, protection = false))
        assertTrue(allows(hut, only(Need.ACCESS_HUTS)), "with ACCESS_HUTS, the exception tag passes the hut")
        assertTrue(allows(hut.copy(exception = false, toggleable = false), all))
    }

    @Test
    fun `CD-LND-08 the protection exception passes a rank that is not hostile`() {
        val exception = plain.copy(exception = true, container = true)
        assertTrue(allows(exception, none))
        assertFalse(allows(exception, none, hostile = true), "a hostile rank goes on to the other rules")
        assertTrue(allows(exception, only(Need.RIGHTCLICK_BLOCK, Need.OPEN_CONTAINER), hostile = true))
    }

    @Test
    fun `CD-LND-08 a door passes with ACCESS_TOGGLEABLES`() {
        val door = plain.copy(toggleable = true)
        assertTrue(allows(door, only(Need.ACCESS_TOGGLEABLES)))
        assertTrue(allows(door, only(Need.ACCESS_TOGGLEABLES), hostile = true))
        assertFalse(allows(door, none), "without ACCESS_TOGGLEABLES, a door needs RIGHTCLICK_BLOCK")
        assertTrue(allows(door, only(Need.RIGHTCLICK_BLOCK)))
    }

    @Test
    fun `CD-LND-08 without colony protection only the hut rule holds`() {
        assertTrue(allows(plain, none, protection = false))
        assertTrue(allows(plain.copy(container = true), none, protection = false))
        assertTrue(allows(plain, none, protection = false, hostile = true))
        assertFalse(allows(plain.copy(hut = true), none, protection = false))
    }

    @Test
    fun `CD-LND-08 any other block needs RIGHTCLICK_BLOCK`() {
        assertFalse(allows(plain, none))
        assertFalse(allows(plain, only(Need.ACCESS_HUTS, Need.ACCESS_TOGGLEABLES, Need.OPEN_CONTAINER)))
        assertTrue(allows(plain, only(Need.RIGHTCLICK_BLOCK)))
    }

    @Test
    fun `CD-LND-08 a container also needs OPEN_CONTAINER`() {
        val container = plain.copy(container = true)
        assertFalse(allows(container, only(Need.RIGHTCLICK_BLOCK)))
        assertFalse(allows(container, only(Need.OPEN_CONTAINER)))
        assertTrue(allows(container, only(Need.RIGHTCLICK_BLOCK, Need.OPEN_CONTAINER)))
    }
}
