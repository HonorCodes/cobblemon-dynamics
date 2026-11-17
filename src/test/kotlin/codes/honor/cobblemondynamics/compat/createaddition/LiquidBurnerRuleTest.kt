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

    /**
     * R88: the burner's mixin never lowers its heat, so a worker adds something only above the heat of its own fuel. In Create Crafts &
     * Additions 1.7.2 no fuel gives Smouldering, a normal fuel Kindled (Fading near its end) and a superheating fuel Seething.
     */
    @Test
    fun `CD-MCH-01 a burner has demand only while the worker's heat is above the heat of its own fuel`() {
        assertTrue(LiquidBurnerRule.adds(worker = "kindled", own = "smouldering"), "no fuel")
        assertTrue(LiquidBurnerRule.adds(worker = "kindled", own = "fading"), "a normal fuel near its end")
        assertTrue(LiquidBurnerRule.adds(worker = "seething", own = "kindled"), "a normal fuel under a level 50 worker")
        assertFalse(LiquidBurnerRule.adds(worker = "kindled", own = "kindled"), "a normal fuel under a level 49 worker")
        assertFalse(LiquidBurnerRule.adds(worker = "seething", own = "seething"), "a superheating fuel")
        assertFalse(LiquidBurnerRule.adds(worker = "kindled", own = "seething"), "a superheating fuel under a level 49 worker")
        assertFalse(LiquidBurnerRule.adds(worker = "blazing", own = "smouldering"), "a heat that Create does not name")
    }
}
