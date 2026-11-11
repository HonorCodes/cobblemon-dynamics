package codes.honor.cobblemondynamics.build

import codes.honor.cobblemondynamics.command.DynamicsCommands
import codes.honor.cobblemondynamics.config.ClientConfig
import codes.honor.cobblemondynamics.config.ServerConfig
import com.electronwill.nightconfig.core.UnmodifiableConfig
import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class PlayerGuideTest {
    private val root = File(System.getProperty("cd.projectDir"))
    private val guide = File(root, "docs/player-guide.md")

    @Test
    fun `CD-REL-08 the player guide has sections for Work Mode, Work Leash, Quick Work, Work Stamina, beds, healers and Charge Cell`() {
        val sections = listOf(
            "Work Mode", "Work Leash", "Quick Work", "Work Stamina", "Beds", "Healers", "Charge Cell", "Server config", "Client config",
        )
        assertSections(sections + "Commands")
    }

    @Test
    fun `CD-REL-08 the player guide has a section for every built-in role`() {
        val files = File(root, ROLES).listFiles { file -> file.extension == "json" }.orEmpty()
        assertTrue(files.isNotEmpty(), "no role files in $ROLES")
        assertSections(files.map { roleName(it.nameWithoutExtension) })
    }

    @Test
    fun `CD-REL-08 the player guide names every server and client config key`() {
        val text = guide.readText()
        val server = keys(ServerConfig.SPEC.spec)
        val client = keys(ClientConfig.SPEC.spec)
        assertTrue(server.isNotEmpty() && server.all(ServerConfig::hasKey), "server config keys: $server")
        assertTrue(client.isNotEmpty() && client.all(ClientConfig::hasKey), "client config keys: $client")
        val missing = (server + client).filterNot { text.contains("`$it`") }
        assertTrue(missing.isEmpty(), "config keys missing from the guide: $missing")
    }

    @Test
    fun `CD-REL-08 the player guide names every command`() {
        val dispatcher = CommandDispatcher<CommandSourceStack>()
        DynamicsCommands.register(dispatcher)
        val commands = dispatcher.root.children.flatMap { root -> root.children.map { "/${root.name} ${it.name}" } }
        assertTrue(commands.isNotEmpty(), "no commands registered")
        val text = guide.readText()
        val missing = commands.filterNot { text.contains(it) }
        assertTrue(missing.isEmpty(), "commands missing from the guide: $missing")
    }

    private fun assertSections(names: List<String>) {
        val headings = guide.readLines().mapNotNull { HEADING.matchEntire(it)?.groupValues?.get(1) }
        val missing = names.filterNot { name -> headings.any { it.equals(name, ignoreCase = true) } }
        assertTrue(missing.isEmpty(), "sections missing from the guide: $missing")
    }

    /** The full path of every value, for example `workArea.horizontalRadius`. */
    private fun keys(config: UnmodifiableConfig, prefix: String = ""): List<String> = config.valueMap().flatMap { (name, value) ->
        val path = if (prefix.isEmpty()) name else "$prefix.$name"
        if (value is UnmodifiableConfig) keys(value, path) else listOf(path)
    }

    /** `brew_stoker` names the Brew Stoker. */
    private fun roleName(id: String): String = id.split('_').joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }

    private companion object {
        const val ROLES = "src/main/resources/data/cobblemon_dynamics/cobblemon_dynamics/roles"
        val HEADING = Regex("""^#{2,6}\s+(.+?)\s*$""")
    }
}
