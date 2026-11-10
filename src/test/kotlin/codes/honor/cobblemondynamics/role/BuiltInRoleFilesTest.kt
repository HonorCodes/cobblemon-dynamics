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
    )

    private val roleDir = File(System.getProperty("cd.projectDir"), "src/main/resources/data/cobblemon_dynamics/cobblemon_dynamics/roles")

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
