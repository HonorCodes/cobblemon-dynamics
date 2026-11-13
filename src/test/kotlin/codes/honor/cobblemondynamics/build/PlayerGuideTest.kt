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

    @Test
    fun `CD-LND-16 the guide has a section Land claims, and the integration page names the tested claim mods`() {
        assertSections(listOf("Land claims"))
        val section = section("Land claims")
        for (text in listOf("FTB Chunks", "Open Parties and Claims", "MineColonies", "while you are offline")) {
            assertTrue(text in section, "the section Land claims of the guide does not say $text")
        }
        val integration = File(root, "docs/modpack-integration.md").readText()
        for (version in listOf("FTB Chunks 2101.1.23", "Open Parties and Claims 0.33.0", "MineColonies 1.1.1420-1.21.1-snapshot")) {
            assertTrue(version in integration, "docs/modpack-integration.md does not name $version")
        }
    }

    /** The lines of the guide's section [name], from its heading to the next heading of the same or a higher level. */
    private fun section(name: String): String {
        val lines = guide.readLines()
        val start = lines.indexOfFirst { HEADING.matchEntire(it)?.groupValues?.get(1).equals(name, ignoreCase = true) }
        val level = lines[start].takeWhile { it == '#' }.length
        val end = (start + 1 until lines.size).firstOrNull { i ->
            HEADING.matchEntire(lines[i]) != null && lines[i].takeWhile { it == '#' }.length <= level
        } ?: lines.size
        return lines.subList(start, end).joinToString("\n")
    }

    /**
     * CD-CRP-16: the section Farming names both roles, the leash, the drops beside the crop and the Magnet that collects them, the
     * apricorn rule and the two target tags.
     */
    @Test
    fun `CD-CRP-16 the guide has a section Farming`() {
        val lines = guide.readLines()
        val start = lines.indexOfFirst { it == "## Farming" }
        assertTrue(start >= 0, "the guide has no section Farming")
        val end = (start + 1 until lines.size).firstOrNull { lines[it].startsWith("## ") } ?: lines.size
        val text = lines.subList(start, end).joinToString("\n")
        val names = listOf(
            "Cultivator",
            "Harvester",
            "Work Leash",
            "on the ground",
            "Magnet",
            "apricorn",
            "Work Stamina",
            "cobblemon_dynamics:cultivator_targets",
            "cobblemon_dynamics:harvester_targets",
        )
        val missing = names.filterNot { it in text }
        assertTrue(missing.isEmpty(), "the section Farming lacks $missing")
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
