package codes.honor.cobblemondynamics.role

import codes.honor.cobblemondynamics.claim.TargetKey
import codes.honor.cobblemondynamics.engine.Assigner
import codes.honor.cobblemondynamics.engine.CandidateView
import codes.honor.cobblemondynamics.engine.WorkerView
import codes.honor.cobblemondynamics.id
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

/** CD-ASG-16 with the mod's own role files: the assigner gives a worker that fits two roles of one target the higher priority. */
class DefaultPriorityTest {
    private val roles: List<RoleDefinition> by lazy {
        BuiltInRoles.dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().sortedBy { it.name }.map(BuiltInRoles::parse)
    }

    /** A scan gives the assigner each target with every role whose handler matches it. */
    private fun target(vararg handlers: String): CandidateView {
        val key = TargetKey.Block(ResourceLocation.withDefaultNamespace("overworld"), 0L)
        return CandidateView(key, Vec3.ZERO, roles.filter { it.handler.toString() in handlers }) { true }
    }

    private fun roleFor(types: Set<String>, target: CandidateView): ResourceLocation? {
        val worker = WorkerView(UUID.randomUUID(), Vec3.ZERO, types, hasFairyType = false, tired = false) { false }
        return Assigner.pickForIdle(worker, listOf(target)) { true }?.second?.id
    }

    /** The approver's ruling (2026-11-10): Dragon-types alone heat Blaze Burners, so a burner has one role, and a Fire-type none. */
    @Test
    fun `CD-ASG-16 CD-SUP-02 a Blaze Burner has only the Superheater, which a Turtonator takes and a Charmander does not`() {
        val burner = target(BURNER_HEAT)
        assertEquals(setOf(id("superheater")), burner.roles.map { it.id }.toSet())
        assertEquals(id("superheater"), roleFor(setOf("fire", "dragon"), burner))
        assertNull(roleFor(setOf("fire"), burner), "a pure Fire-type")
    }

    @Test
    fun `CD-ASG-16 a Poison and Fire Salazzle on a brewing stand works as the Brew Stoker`() {
        val stand = target(BREWING_FUEL, BREWING_SPEED)
        assertEquals(setOf(id("brew_stoker"), id("brewer")), stand.roles.map { it.id }.toSet())
        assertEquals(id("brew_stoker"), roleFor(setOf("poison", "fire"), stand))
        assertEquals(id("brewer"), roleFor(setOf("poison"), stand), "a pure Poison-type")
    }

    /** Both leash-only roles fit a Create basin, which exposes a fluid and an item handler. Both have priority 0, and `pump` sorts first. */
    @Test
    fun `CD-LSH-16 with the default files a Slowbro on a basin gets the Pump, not Telekinesis`() {
        val pump = BuiltInRoles.parse("pump")
        val telekinesis = BuiltInRoles.parse("telekinesis")
        assertEquals(pump.priority, telekinesis.priority)
        val slowbro = listOf("water", "psychic")
        assertEquals(pump, RoleSelection.best(listOf(telekinesis, pump).filter { RoleSelection.isEligible(it, slowbro) }))
    }

    private companion object {
        const val BURNER_HEAT = "cobblemon_dynamics:burner_heat"
        const val BREWING_FUEL = "cobblemon_dynamics:brewing_fuel"
        const val BREWING_SPEED = "cobblemon_dynamics:brewing_speed"
    }
}
