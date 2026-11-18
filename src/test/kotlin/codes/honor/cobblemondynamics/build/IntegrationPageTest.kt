package codes.honor.cobblemondynamics.build

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** The modpack integration page, which pack makers read (CD-CRP-17). */
class IntegrationPageTest {
    private val page = File(System.getProperty("cd.projectDir"), "docs/modpack-integration.md")

    /** The text of the section [heading], from its heading to the next heading of its level or above. */
    private fun section(heading: String): String {
        val lines = page.readLines()
        val start = lines.indexOfFirst { it.trimStart('#').trim() == heading && it.startsWith("#") }
        assertTrue(start >= 0, "the integration page has no section $heading")
        val level = lines[start].takeWhile { it == '#' }.length
        val end = (start + 1 until lines.size).firstOrNull { i ->
            lines[i].startsWith("#") && lines[i].takeWhile { it == '#' }.length <= level
        } ?: lines.size
        return lines.subList(start, end).joinToString("\n")
    }

    @Test
    fun `CD-CRP-17 the section Farming roles names the tags, the families and the COHO 2-5-0 versions`() {
        val text = section("Farming roles")
        val names = listOf(
            "cobblemon_dynamics:cultivator_targets",
            "cobblemon_dynamics:harvester_targets",
            "cobblemon_dynamics:harvest/never",
            "cobblemon_dynamics:harvest/pick_to_age_0",
            "cobblemon_dynamics:harvest/pick_to_age_1",
            "cobblemon_dynamics:harvest/upper_half",
            "cobblemon_dynamics:harvest/break_and_replant",
        )
        // The versions of the crop mods that RES8 read, as COHO 2.5.0 ships them (ADR-0015).
        val versions = listOf(
            "1.8.4", "1.21.1-1.3.4", "0.1.4", "1.21.1-1.4.5", "1.21.1-4.0.2", "12.4.2-194", "1.5.10", "1.1.5.1", "2.6.3", "0.9.6",
            "5.13.5", "1.227.0", "0.6.3", "1.0.9",
        )
        val missing = (names + versions).filterNot { it in text }
        assertTrue(missing.isEmpty(), "the section Farming roles lacks $missing")
    }

    @Test
    fun `CD-OPT-17 the section Mods that the roles support names each mod and version of ADR-0016, and each tag and data map of 1-2`() {
        val root = File(System.getProperty("cd.projectDir"))
        val data = File(root, "src/main/resources/data/cobblemon_dynamics")
        val absent = TAGS_OF_1_2.filterNot { File(data, "tags/block/$it.json").isFile } +
            DATA_MAPS_OF_1_2.filterNot { File(data, "data_maps/block/$it.json").isFile }
        assertTrue(absent.isEmpty(), "CD-OPT-17: the mod has no tag or data map $absent")
        val text = section("Mods that the roles support")
        val names = SupportedMods.read(root).flatMap { listOf(it.name, it.version) } +
            TAGS_OF_1_2.map { "#cobblemon_dynamics:$it" } +
            DATA_MAPS_OF_1_2.map { "cobblemon_dynamics:$it" }
        val missing = names.filterNot { it in text }
        assertTrue(missing.isEmpty(), "CD-OPT-17: the section Mods that the roles support lacks $missing")
    }

    private companion object {
        /** The block tags that 1.2 adds, which the table "Tags and the data map of 1.2" of the section lists. */
        val TAGS_OF_1_2 = listOf(
            "lumberjack_targets",
            "lumberjack/never",
            "lumberjack/never_saplings",
            "lumberjack/needs_four",
            "needs_adjacent_water",
            "stoker/never",
            "cultivator/never",
            "superheater/heat_users",
            "heater_targets",
            "crystal_tender_targets",
            "crystal/self_growing",
            "crystal/scheduled_tick",
            "crystal/buds",
            "muse_targets",
            "logistics/no_fallback",
            "harvest/cave_vines",
        )

        /** The block data map that 1.2 adds. */
        val DATA_MAPS_OF_1_2 = listOf("harvest_loot")
    }
}
