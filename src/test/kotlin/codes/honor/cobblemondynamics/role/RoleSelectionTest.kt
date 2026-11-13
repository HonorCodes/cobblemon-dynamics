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
        effect = EffectDefinition(40, 40, listOf("special"), emptyList(), emptyList()),
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
        val brewer = role("brewer", setOf("poison"), priority = 0)
        val brewStoker = role("brew_stoker", setOf("fire"), priority = 10)
        assertEquals(brewStoker, RoleSelection.best(listOf(brewer, brewStoker)))
        assertEquals(brewStoker, RoleSelection.best(listOf(brewStoker, brewer)))
    }

    @Test
    fun `CD-LSH-16 on a priority tie the role id that sorts first wins`() {
        val pump = role("pump", setOf("water"), autoAssign = false)
        val telekinesis = role("telekinesis", setOf("psychic"), autoAssign = false)
        assertEquals(pump, RoleSelection.best(listOf(telekinesis, pump)))
    }

    private val fanFire = role("fan_fire", setOf("fire"), autoAssign = false, handler = "cobblemon_dynamics:fan_catalyst")
    private val fanGhost = role("fan_ghost", setOf("ghost"), autoAssign = false, handler = "cobblemon_dynamics:fan_catalyst")

    /** Ruling 1 of the Phase 2 plan: between roles of one handler, the Pokémon's primary type wins, then its secondary type. */
    @Test
    fun `CD-LSH-16 with ruling 1 the primary type picks between roles of one handler`() {
        assertEquals(fanFire, RoleSelection.best(listOf(fanGhost, fanFire), listOf("fire", "ghost")))
        assertEquals(fanGhost, RoleSelection.best(listOf(fanFire, fanGhost), listOf("ghost", "fire")))
        assertEquals(fanGhost, RoleSelection.best(listOf(fanFire, fanGhost), listOf("ghost")))
        // A caller that names no types gets the id order.
        assertEquals(fanFire, RoleSelection.best(listOf(fanGhost, fanFire)))
    }

    @Test
    fun `CD-LSH-16 the type order never beats a higher priority or the id order between handlers`() {
        val fanWater = role("fan_water", setOf("water"), priority = 5, autoAssign = false, handler = "cobblemon_dynamics:fan_catalyst")
        assertEquals(fanWater, RoleSelection.best(listOf(fanFire, fanWater), listOf("fire", "water")))
        val pump = role("pump", setOf("water"), autoAssign = false, handler = "cobblemon_dynamics:fluid_fill")
        val telekinesis = role("telekinesis", setOf("psychic"), autoAssign = false, handler = "cobblemon_dynamics:telekinesis")
        assertEquals(pump, RoleSelection.best(listOf(telekinesis, pump), listOf("psychic", "water")))
    }

    private val cultivator = role("cultivator", setOf("ground"), handler = "cobblemon_dynamics:cultivator")
    private val harvester = role("harvester", setOf("grass"), handler = "cobblemon_dynamics:harvester")

    /** The two farming roles are one group for the primary type, as the roles of one handler are (CD-CRP-22). */
    private val farming: (RoleDefinition, RoleDefinition) -> Boolean = { a, b ->
        a.handler == b.handler || setOf(a, b).all { it == cultivator || it == harvester }
    }

    @Test
    fun `CD-CRP-21 Torterra at fully grown wheat gets the Harvester and at wheat of age 3 the Cultivator`() {
        val torterra = listOf("grass", "ground")
        val grown = RoleSelection.best(listOf(cultivator, harvester), torterra, farming) { it == harvester }
        assertEquals(harvester, grown)
        val growing = RoleSelection.best(listOf(cultivator, harvester), torterra, farming) { it == cultivator }
        assertEquals(cultivator, growing)
    }

    @Test
    fun `CD-CRP-22 Torterra binds the Harvester and Toedscool the Cultivator`() {
        assertEquals(harvester, RoleSelection.best(listOf(cultivator, harvester), listOf("grass", "ground"), farming))
        assertEquals(cultivator, RoleSelection.best(listOf(harvester, cultivator), listOf("ground", "grass"), farming))
        // Without the farming group, the id order decides between two handlers, as in 1.0.
        assertEquals(cultivator, RoleSelection.best(listOf(harvester, cultivator), listOf("grass", "ground")))
    }
}
