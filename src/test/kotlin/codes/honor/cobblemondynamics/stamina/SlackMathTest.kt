package codes.honor.cobblemondynamics.stamina

import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SlackMathTest {
    private fun nat(name: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath("cobblemon", name)

    @Test
    fun `CD-PRG-15 all 25 Natures map to Driven 0_35, Steady 0_75, Distracted 1_15, and Lazy 1_60, and effectiveNature wins`() {
        val driven = listOf("timid", "hasty", "jolly", "naive", "hardy", "serious")
        val steady = listOf("adamant", "modest", "careful", "calm", "bold", "impish", "docile", "bashful")
        val distracted = listOf("lonely", "brave", "naughty", "mild", "quiet", "rash", "gentle", "sassy", "quirky")
        val lazy = listOf("relaxed", "lax")
        assertEquals(25, (driven + steady + distracted + lazy).toSet().size)

        for (n in driven) assertEquals(0.35, SlackMath.natureFactor(n.uppercase()), 1e-9, n)
        for (n in steady) assertEquals(0.75, SlackMath.natureFactor(n), 1e-9, n)
        for (n in distracted) assertEquals(1.15, SlackMath.natureFactor(n), 1e-9, n)
        for (n in lazy) assertEquals(1.60, SlackMath.natureFactor(n), 1e-9, n)

        assertEquals(0.35, SlackMath.natureFactor(effectiveNature = nat("jolly"), baseNature = nat("relaxed")), 1e-9)
        assertEquals(1.60, SlackMath.natureFactor(effectiveNature = null, baseNature = nat("relaxed")), 1e-9)
    }

    @Test
    fun `CD-PRG-15 bed tiers map to 1_00 0_85 0_70 0_55 0_35 from max of workArea and lastSlept, and friendship curves 1_0 to 0_25`() {
        val expectedBeds = mapOf(0 to 1.00, 1 to 0.85, 2 to 0.70, 3 to 0.55, 4 to 0.35)
        for ((tier, factor) in expectedBeds) assertEquals(factor, SlackMath.bedFactor(tier), 1e-9, "tier $tier")
        assertEquals(3, SlackMath.effectiveBedTier(areaMaxTier = 1, lastSleptTier = 3))
        assertEquals(4, SlackMath.effectiveBedTier(areaMaxTier = 4, lastSleptTier = 2))

        assertEquals(1.0, SlackMath.friendshipFactor(0), 1e-9)
        assertEquals(1.0 - 0.75 * (128.0 / 255.0), SlackMath.friendshipFactor(128), 1e-9)
        assertEquals(0.25, SlackMath.friendshipFactor(255), 1e-9)
    }

    @Test
    fun `CD-PRG-14 probability clamps to 0_0 through 1_0 and 0_0 baseSlackChance never slacks`() {
        val zero = SlackMath.probability(0.0, nat("relaxed"), nat("relaxed"), 0, 0, 0)
        assertEquals(0.0, zero, 1e-9)
        assertFalse(SlackMath.shouldSlack(zero, 0.0))

        val jollyStarlitMax = SlackMath.probability(0.12, nat("jolly"), nat("jolly"), 4, 0, 255)
        assertEquals(0.12 * 0.35 * 0.35 * 0.25, jollyStarlitMax, 1e-9)

        val clamped = SlackMath.probability(1.0, nat("relaxed"), nat("relaxed"), 0, 0, 0)
        assertEquals(1.0, clamped, 1e-9)
        assertTrue(SlackMath.shouldSlack(clamped, 0.999999))
    }
}
