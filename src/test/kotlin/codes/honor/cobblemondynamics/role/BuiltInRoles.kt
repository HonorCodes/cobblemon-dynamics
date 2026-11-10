package codes.honor.cobblemondynamics.role

import com.google.gson.JsonParser
import net.minecraft.resources.ResourceLocation
import java.io.File

/** The mod's own role files, read from the source tree as a data pack load would parse them. */
object BuiltInRoles {
    val dir = File(System.getProperty("cd.projectDir"), "src/main/resources/data/cobblemon_dynamics/cobblemon_dynamics/roles")

    fun parse(name: String): RoleDefinition = parse(File(dir, "$name.json"))

    fun parse(file: File): RoleDefinition {
        val id = ResourceLocation.fromNamespaceAndPath("cobblemon_dynamics", file.nameWithoutExtension)
        return RoleCodec.parse(id, JsonParser.parseString(file.readText())).getOrThrow()
    }
}
