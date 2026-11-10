package codes.honor.cobblemondynamics.role

import com.google.gson.JsonParser
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

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
    )

    private val roleDir = File(System.getProperty("cd.projectDir"), "src/main/resources/data/cobblemon_dynamics/cobblemon_dynamics/roles")

    private fun parse(name: String): RoleDefinition {
        val id = ResourceLocation.fromNamespaceAndPath("cobblemon_dynamics", name)
        return RoleCodec.parse(id, JsonParser.parseString(File(roleDir, "$name.json").readText())).getOrThrow()
    }

    /**
     * Cobblemon's `watergun` timeline emits the spray and the actor burst from the user, aimed at the target, and only the two
     * `target` particles on the target. Target particles play at the work focus entity, so the spray must be an actor particle.
     */
    @Test
    fun `CD-FX-04 the Wheel Driver sprays from the worker and splashes at the wheel`() {
        val effect = parse("wheel_driver").effect
        assertEquals(listOf("cobblemon:watergun_spray", "cobblemon:watergun_actor"), effect.actorParticles.map { it.toString() })
        assertEquals(listOf("cobblemon:watergun_target", "cobblemon:watergun_targetfoam"), effect.targetParticles.map { it.toString() })
        assertEquals(listOf("watergun", "spray", "beam", "special", "cry"), effect.animationsWithCry)
    }

    @Test
    fun `CD-FX-02 every built-in role file matches the Role table`() {
        val files = roleDir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().sortedBy { it.name }
        val actual = files.associate { f ->
            val id = ResourceLocation.fromNamespaceAndPath("cobblemon_dynamics", f.nameWithoutExtension)
            val role = RoleCodec.parse(id, JsonParser.parseString(f.readText())).getOrThrow()
            f.nameWithoutExtension to
                Row(role.handler.toString(), role.types, role.autoAssign, role.priority, role.effect.animations.first())
        }
        assertEquals(roleTable, actual)
    }
}
