package codes.honor.cobblemondynamics.fx

import codes.honor.cobblemondynamics.OnceLogger
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ParticleCatalogTest {
    @Test
    fun `CD-FX-11 a missing particle id warns once and the rest still play`() {
        val missing = ResourceLocation.parse("cobblemon:watergun_spray")
        val present = ResourceLocation.parse("cobblemon:watergun_actor")
        val warnings = mutableListOf<String>()
        val checked = CheckedParticles({ it != missing }, OnceLogger(warnings::add))
        repeat(3) { assertEquals(listOf(present), checked.filter(listOf(missing, present))) }
        assertEquals(1, warnings.size)
        assertTrue(warnings.single().contains("cobblemon:watergun_spray"))
    }
}
