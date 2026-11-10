package codes.honor.cobblemondynamics.role

import com.google.gson.JsonObject
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

fun role(path: String, types: Set<String>, priority: Int = 0, autoAssign: Boolean = true, handler: String = "cobblemon_dynamics:test") =
    RoleDefinition(
        id = ResourceLocation.fromNamespaceAndPath("cobblemon_dynamics", path),
        handler = ResourceLocation.parse(handler),
        types = types,
        autoAssign = autoAssign,
        priority = priority,
        range = 6.0,
        params = JsonObject(),
        effect = EffectDefinition(40, listOf("special"), emptyList(), emptyList(), null),
    )

class RoleSelectionTest {
    @Test
    fun `CD-ASG-02 the secondary type counts and an unlisted type does not`() {
        val wheelDriver = role("wheel_driver", setOf("water"))
        assertTrue(RoleSelection.isEligible(wheelDriver, listOf("water", "flying"))) // Gyarados
        assertTrue(RoleSelection.isEligible(wheelDriver, listOf("flying", "water")))
        assertFalse(RoleSelection.isEligible(wheelDriver, listOf("electric"))) // Pikachu
    }

    @Test
    fun `CD-ASG-16 the higher priority wins`() {
        val burnerKeeper = role("burner_keeper", setOf("fire"), priority = 0)
        val superheater = role("superheater", setOf("dragon"), priority = 10)
        assertEquals(superheater, RoleSelection.best(listOf(burnerKeeper, superheater)))
        val brewer = role("brewer", setOf("poison"), priority = 0)
        val brewStoker = role("brew_stoker", setOf("fire"), priority = 10)
        assertEquals(brewStoker, RoleSelection.best(listOf(brewer, brewStoker)))
    }

    @Test
    fun `CD-LSH-16 on a priority tie the role id that sorts first wins`() {
        val pump = role("pump", setOf("water"), autoAssign = false)
        val telekinesis = role("telekinesis", setOf("psychic"), autoAssign = false)
        assertEquals(pump, RoleSelection.best(listOf(telekinesis, pump)))
    }
}
