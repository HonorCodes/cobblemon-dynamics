package codes.honor.cobblemondynamics.client

import codes.honor.cobblemondynamics.MixinState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PanelMixinCheckTest {
    /** The button and the head bars never ask this check, so a missing panel mixin cannot turn them off. */
    @Test
    fun `CD-UI-13 with the pasture-list mixin flag false the bars are off with one warning and nothing throws`() {
        val warnings = mutableListOf<String>()
        val check = PanelMixinCheck(applied = { it != MixinState.PASTURE_WIDGET }, warn = warnings::add)
        repeat(3) { assertFalse(check.barsOn()) }
        assertTrue(check.listShortened())
        assertEquals(1, warnings.size, warnings.toString())
        assertTrue(MixinState.PASTURE_WIDGET in warnings.single(), warnings.toString())
    }

    @Test
    fun `each missing panel mixin warns once`() {
        val warnings = mutableListOf<String>()
        val check = PanelMixinCheck(applied = { false }, warn = warnings::add)
        repeat(2) {
            assertFalse(check.barsOn())
            assertFalse(check.listShortened())
        }
        assertEquals(2, warnings.size, warnings.toString())
        assertTrue(warnings.any { MixinState.PASTURE_LIST in it }, warnings.toString())
    }

    @Test
    fun `applied mixins warn nothing`() {
        val warnings = mutableListOf<String>()
        val check = PanelMixinCheck(applied = { true }, warn = warnings::add)
        assertTrue(check.barsOn())
        assertTrue(check.listShortened())
        assertTrue(warnings.isEmpty(), warnings.toString())
    }

    /** No mixin applies in a unit test, so the real flags start false. */
    @Test
    fun `the real flags of a unit test run read as not applied`() {
        assertFalse(MixinState.applied(MixinState.PASTURE_WIDGET))
        assertFalse(MixinState.applied(MixinState.PASTURE_LIST))
    }
}
