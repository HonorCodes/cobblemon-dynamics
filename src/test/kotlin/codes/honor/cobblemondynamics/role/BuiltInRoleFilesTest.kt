package codes.honor.cobblemondynamics.role

import codes.honor.cobblemondynamics.engine.WorkSpots
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuiltInRoleFilesTest {
    private data class Row(
        val handler: String,
        val types: Set<String>,
        val autoAssign: Boolean,
        val priority: Int,
        val firstAnimation: String,
    )

    /** The Role table of the spec sheet, by role id. M4 adds the other Phase 1 roles. */
    private val roleTable = mapOf(
        "stoker" to Row("cobblemon_dynamics:furnace", setOf("fire"), true, 0, "flamethrower"),
        "wheel_driver" to Row("cobblemon_dynamics:water_wheel", setOf("water"), true, 0, "watergun"),
        "cranker" to Row("cobblemon_dynamics:hand_crank", setOf("fighting", "normal"), true, 0, "pound"),
    )

    /**
     * Cobblemon's `watergun` timeline emits the spray and the actor burst from the user, aimed at the target, and only the two
     * `target` particles on the target. Target particles play at the work focus entity, so the spray must be an actor particle.
     */
    @Test
    fun `CD-FX-04 the Wheel Driver sprays from the worker and splashes at the wheel`() {
        val effect = BuiltInRoles.parse("wheel_driver").effect
        assertEquals(listOf("cobblemon:watergun_spray", "cobblemon:watergun_actor"), effect.actorParticles.map { it.toString() })
        assertEquals(listOf("cobblemon:watergun_target", "cobblemon:watergun_targetfoam"), effect.targetParticles.map { it.toString() })
        assertEquals(listOf("watergun", "spray", "beam", "special", "cry"), effect.animationsWithCry)
    }

    /** Cobblemon's `pound` timeline emits both of its particles on the target only. */
    @Test
    fun `CD-FX-04 the Cranker's Pound lands on the crank`() {
        val effect = BuiltInRoles.parse("cranker").effect
        assertEquals(emptyList<String>(), effect.actorParticles.map { it.toString() })
        assertEquals(listOf("cobblemon:pound_target", "cobblemon:pound_targetsparks"), effect.targetParticles.map { it.toString() })
        assertEquals(listOf("pound", "punch", "tail", "physical", "cry"), effect.animationsWithCry)
    }

    @Test
    fun `CD-CRK-01 Machop and Rattata may crank, and Pikachu may not`() {
        val cranker = BuiltInRoles.parse("cranker")
        assertTrue(RoleSelection.isEligible(cranker, listOf("fighting")), "Machop")
        assertTrue(RoleSelection.isEligible(cranker, listOf("normal")), "Rattata")
        assertFalse(RoleSelection.isEligible(cranker, listOf("electric")), "Pikachu")
    }

    @Test
    fun `CD-ASG-10 the default role files give the Stoker 6 blocks and the Cranker 2`() {
        val center = Vec3(0.5, 1.5, 0.5)
        val stoker = BuiltInRoles.parse("stoker").range
        assertTrue(WorkSpots.inRange(center.add(6.0, 0.0, 0.0), center, stoker))
        assertFalse(WorkSpots.inRange(center.add(6.1, 0.0, 0.0), center, stoker))
        val cranker = BuiltInRoles.parse("cranker").range
        assertTrue(WorkSpots.inRange(center.add(0.0, 0.0, 2.0), center, cranker))
        assertFalse(WorkSpots.inRange(center.add(0.0, 0.0, 2.1), center, cranker))
    }

    @Test
    fun `CD-FX-02 every built-in role file matches the Role table`() {
        val files = BuiltInRoles.dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().sortedBy { it.name }
        val actual = files.associate { f ->
            val role = BuiltInRoles.parse(f)
            val row = Row(role.handler.toString(), role.types, role.autoAssign, role.priority, role.effect.animations.first())
            f.nameWithoutExtension to row
        }
        assertEquals(roleTable, actual)
    }
}
