package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.config.EngineSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DualFairyRuleTest {
    private val settings = EngineSettings.DEFAULT

    @Test
    fun `CD-TEAM-09 pure Fairy worker never receives auto-assign machine types`() {
        assertEquals(
            emptySet<String>(),
            DualFairyRule.autoAssignTypes(
                types = setOf("fairy"),
                hasFairyType = true,
                leashed = false,
                tired = false,
                staminaFraction = 1.0,
                allyNeedsHeal = false,
                settings = settings,
            ),
        )
        assertEquals(
            emptySet<String>(),
            DualFairyRule.autoAssignTypes(
                types = setOf("fairy"),
                hasFairyType = true,
                leashed = false,
                tired = false,
                staminaFraction = 0.10,
                allyNeedsHeal = true,
                settings = settings,
            ),
        )
    }

    @Test
    fun `CD-TEAM-09 unbound dual-type Fairy gets non-Fairy types when no ally needs healing or stamina is below healCost`() {
        val types = linkedSetOf("water", "fairy")
        assertEquals(
            setOf("water"),
            DualFairyRule.autoAssignTypes(
                types = types,
                hasFairyType = true,
                leashed = false,
                tired = false,
                staminaFraction = 1.0,
                allyNeedsHeal = false,
                settings = settings,
            ),
        )
        assertEquals(
            setOf("water"),
            DualFairyRule.autoAssignTypes(
                types = types,
                hasFairyType = true,
                leashed = false,
                tired = false,
                staminaFraction = 0.20,
                allyNeedsHeal = true,
                settings = settings,
            ),
        )
        assertEquals(
            emptySet<String>(),
            DualFairyRule.autoAssignTypes(
                types = types,
                hasFairyType = true,
                leashed = false,
                tired = false,
                staminaFraction = 0.25,
                allyNeedsHeal = true,
                settings = settings,
            ),
        )
        assertEquals(
            emptySet<String>(),
            DualFairyRule.autoAssignTypes(
                types = types,
                hasFairyType = true,
                leashed = false,
                tired = true,
                staminaFraction = 0.0,
                allyNeedsHeal = false,
                settings = settings,
            ),
        )
    }

    @Test
    fun `CD-TEAM-10 preempts unbound dual-type Fairy auto work claim when ally needs healing and keeps leashed claim`() {
        assertTrue(
            DualFairyRule.shouldPreemptForHeal(
                hasFairyType = true,
                leashed = false,
                holdsWorkClaim = true,
                tired = false,
                staminaFraction = 0.80,
                allyNeedsHeal = true,
                settings = settings,
            ),
        )
        assertFalse(
            DualFairyRule.shouldPreemptForHeal(
                hasFairyType = true,
                leashed = true,
                holdsWorkClaim = true,
                tired = false,
                staminaFraction = 0.80,
                allyNeedsHeal = true,
                settings = settings,
            ),
        )
        assertFalse(
            DualFairyRule.shouldPreemptForHeal(
                hasFairyType = true,
                leashed = false,
                holdsWorkClaim = true,
                tired = false,
                staminaFraction = 0.20,
                allyNeedsHeal = true,
                settings = settings,
            ),
        )
        assertFalse(
            DualFairyRule.shouldPreemptForHeal(
                hasFairyType = true,
                leashed = false,
                holdsWorkClaim = true,
                tired = false,
                staminaFraction = 0.80,
                allyNeedsHeal = false,
                settings = settings,
            ),
        )
    }

    @Test
    fun `CD-TEAM-09 CD-TEAM-10 trait-scaled healCost governs dual-Fairy auto-assign and heal preemption`() {
        val types = linkedSetOf("psychic", "fairy")
        assertEquals(
            emptySet<String>(),
            DualFairyRule.autoAssignTypes(
                types = types,
                hasFairyType = true,
                leashed = false,
                tired = false,
                staminaFraction = 0.21,
                allyNeedsHeal = true,
                settings = settings,
                healMultiplier = 1.25,
                isMajestic = false,
            ),
        )
        assertTrue(
            DualFairyRule.shouldPreemptForHeal(
                hasFairyType = true,
                leashed = false,
                holdsWorkClaim = true,
                tired = false,
                staminaFraction = 0.21,
                allyNeedsHeal = true,
                settings = settings,
                healMultiplier = 1.25,
                isMajestic = false,
            ),
        )
    }
}
