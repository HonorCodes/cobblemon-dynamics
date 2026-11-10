package codes.honor.cobblemondynamics.fx

import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HealEffectTest {
    @Test
    fun `CD-HEAL-16 Floral Healing runs when the registry has it, the fallback otherwise`() {
        val id = ResourceLocation.parse("cobblemon:floralhealing")
        val effect = Any()
        assertEquals(HealEffect.Choice.FloralHealing(effect), HealEffect.choose { if (it == id) effect else null })
        assertEquals(HealEffect.Choice.Fallback, HealEffect.choose { null })
    }

    @Test
    fun `CD-HEAL-15 the fallback plays the healer's special animation and the heal circles on the ally, and never a cry`() {
        assertEquals(listOf("special"), HealEffect.FALLBACK_ANIMATIONS)
        val particles = listOf("cobblemon:heal_circles").map(ResourceLocation::parse)
        assertEquals(particles, HealEffect.FALLBACK_PARTICLES)
    }
}
