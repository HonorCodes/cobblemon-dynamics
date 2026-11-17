package codes.honor.cobblemondynamics.compat.createaddition

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Heat levels by Create's names: `smouldering`, `fading`, `kindled`, `seething`. */
class LiquidBurnerRuleTest {
    /**
     * A fueled Liquid Blaze Burner writes its heat into its block state only when its fuel changes, so Seething heat that a worker left,
     * through a lapsed grant or a restart, would stay until then. Its own fuel gives Seething only with a superheating fuel.
     */
    @Test
    fun `CD-MCH-01 Seething heat that no grant and no fuel gives is dropped`() {
        assertTrue(LiquidBurnerRule.stale(granted = false, shown = "seething", own = "kindled"))
        assertTrue(LiquidBurnerRule.stale(granted = false, shown = "seething", own = "smouldering"))
    }

    @Test
    fun `CD-MCH-01 a worked burner, a superheating fuel and every lower heat keep their state`() {
        assertFalse(LiquidBurnerRule.stale(granted = true, shown = "seething", own = "kindled"), "a worker's heat")
        assertFalse(LiquidBurnerRule.stale(granted = false, shown = "seething", own = "seething"), "its own superheating fuel")
        // The burner's own block state can lag its fuel below Seething, which is its own rule, so the worker never touches it.
        assertFalse(LiquidBurnerRule.stale(granted = false, shown = "kindled", own = "fading"))
        assertFalse(LiquidBurnerRule.stale(granted = false, shown = "kindled", own = "smouldering"))
    }
}
