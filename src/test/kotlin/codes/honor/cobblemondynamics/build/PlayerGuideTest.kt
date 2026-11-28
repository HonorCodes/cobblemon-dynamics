package codes.honor.cobblemondynamics.build

import codes.honor.cobblemondynamics.command.DynamicsCommands
import codes.honor.cobblemondynamics.config.ClientConfig
import codes.honor.cobblemondynamics.config.ServerConfig
import codes.honor.cobblemondynamics.role.RoleConditions
import com.electronwill.nightconfig.core.UnmodifiableConfig
import com.google.gson.JsonParser
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
        // The role file of another mod, such as stoker_ironfurnaces, is that mod's part of a role, which the role's own section covers.
        val conditional = files.filter { JsonParser.parseString(it.readText()).asJsonObject.has(RoleConditions.KEY) }.toSet()
        val roles = files.filterNot { it in conditional }.map { it.nameWithoutExtension }.toSet()
        assertSections(files.map { roleName(if (it in conditional) roleOf(it.nameWithoutExtension, roles) else it.nameWithoutExtension) })
    }

    /** The longest name of [roles] that [file] starts with, as `stoker` for `stoker_ironfurnaces`, or else its first word. */
    private fun roleOf(file: String, roles: Set<String>): String =
        roles.filter { file.startsWith("${it}_") }.maxByOrNull { it.length } ?: file.substringBefore('_')

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

    @Test
    fun `CD-OPT-16 the guide has the sections of the new roles, and Other mods names each mod of ADR-0016`() {
        assertSections(listOf("Lumberjack", "Experience Collector", "Soul Collector", "Freezer", "Heater", "Other mods"))
        val other = section("Other mods")
        val missing = SupportedMods.read(root).map { it.name }.filterNot { other.contains(it, ignoreCase = true) }
        assertTrue(missing.isEmpty(), "CD-OPT-16: the section Other mods of the guide does not name $missing")
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

    /** CD-PCK-13: each page that lists the roles names the Picker, its two types, its tag, the leash and the drops. */
    @Test
    fun `CD-PCK-13 the guide, the README, the roles page and the integration page name the Picker`() {
        assertSections(listOf("Picker"))
        val picker = section("Picker")
        val names = listOf("Bug", "Flying", "apricorn", "#cobblemon_dynamics:picker_targets", "never binds a Picker", "to the ground")
        val missing = names.filterNot { it in picker }
        assertTrue(missing.isEmpty(), "CD-PCK-13: the section Picker of the guide lacks $missing")
        val readme = File(root, "README.md").readLines()
        assertTrue(
            readme.any {
                it.startsWith("| Bug and Flying, both | Picker |")
            },
            "CD-PCK-13: the README's roles table has no Picker row",
        )
        val roles = File(root, "docs/roles.md").readLines()
        assertTrue(
            roles.any {
                it.startsWith("| Picker | Bug and Flying, both |")
            },
            "CD-PCK-13: docs/roles.md has no table row of the Picker",
        )
        for (type in listOf("| Bug |", "| Flying |")) {
            assertTrue(
                roles.any { it.startsWith(type) && "Picker" in it },
                "CD-PCK-13: the type index of docs/roles.md has no Picker in its row $type",
            )
        }
        val integration = File(root, "docs/modpack-integration.md").readText()
        for (text in listOf("Picker", "#cobblemon_dynamics:picker_targets", "type_match", "leash")) {
            assertTrue(text in integration, "CD-PCK-13: docs/modpack-integration.md does not name $text")
        }
    }

    @Test
    fun `CD-SNT-01 CD-SNT-05 CD-SNT-06 the guide, the README, the roles page and en_us name the Sentry, Nocturnal and Campfire Pot`() {
        assertSections(listOf("Sentry"))
        val sentry = section("Sentry")
        for (text in listOf("Dark", "Snarl", "natural", "spawners", "Cobblemon", "1 tick")) {
            assertTrue(text in sentry, "CD-SNT-01: the section Sentry of the guide lacks $text")
        }
        assertTrue("Campfire Pot" in section("Stoker"), "CD-SNT-06: the section Stoker of the guide does not name Campfire Pot")
        val stamina = section("Work Stamina")
        assertTrue("Dark" in stamina && "half" in stamina, "CD-SNT-05: the section Work Stamina lacks the Dark night rule")
        val readmeText = File(root, "README.md").readText()
        assertTrue(!readmeText.contains("Dark has no role"), "README still says Dark has no role")
        val readme = readmeText.lines()
        assertTrue(readme.any { it.startsWith("| Dark | Sentry |") }, "README roles table has no Sentry row")
        val rolesText = File(root, "docs/roles.md").readText()
        assertTrue(!rolesText.contains("Dark has no role"), "docs/roles.md still says Dark has no role")
        val roles = rolesText.lines()
        assertTrue(roles.any { it.startsWith("| Sentry | Dark |") }, "docs/roles.md has no table row of the Sentry")
        assertTrue(roles.any { it.startsWith("| Dark |") && "Sentry" in it }, "docs/roles.md type index has no Sentry in its Dark row")
        val lang = JsonParser.parseString(
            File(root, "src/main/resources/assets/cobblemon_dynamics/lang/en_us.json").readText(),
        ).asJsonObject
        assertTrue(lang.get("cobblemon_dynamics.role.sentry")?.asString == "Sentry", "en_us.json lacks cobblemon_dynamics.role.sentry")
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
