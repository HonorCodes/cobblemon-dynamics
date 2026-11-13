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
}
