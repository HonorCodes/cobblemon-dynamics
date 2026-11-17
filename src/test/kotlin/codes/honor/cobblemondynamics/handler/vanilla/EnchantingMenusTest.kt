package codes.honor.cobblemondynamics.handler.vanilla

import codes.honor.cobblemondynamics.MuseStationMenu
import net.minecraft.world.inventory.ContainerLevelAccess
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EnchantingMenusTest {
    /** An infuser's menu, which its mixin makes a station menu. */
    private class Station : MuseStationMenu {
        var refreshed = 0

        override fun `cobblemon_dynamics$station`(): ContainerLevelAccess = error("the refresh reads no block")

        override fun `cobblemon_dynamics$refreshPower`() {
            refreshed++
        }
    }

    @Test
    fun `CD-FIX-06 a Muse's start or stop gives an infuser's menu its power alone, and other menus compute their offers again`() {
        val infuser = Station()
        var recomputed = 0
        EnchantingMenus.refreshMenu(infuser) { recomputed++ }
        assertEquals(1, infuser.refreshed, "the infuser's menu did not count its power again")
        assertEquals(0, recomputed, "the infuser's menu computed its offers again, which resets the player's chosen levels")
        // The vanilla menu and Easy Magic's menu, which extends it, keep their update.
        EnchantingMenus.refreshMenu(Any()) { recomputed++ }
        assertEquals(1, recomputed)
        assertEquals(1, infuser.refreshed)
    }
}
