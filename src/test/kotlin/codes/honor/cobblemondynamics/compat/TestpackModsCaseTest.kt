package codes.honor.cobblemondynamics.compat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.io.File

/**
 * CD-OPT-11, CD-OPT-15: the test pack's case `mods` expects the compat line of each mod of [OptionalMods] that logs one, with its
 * adapters, and no line for a mod that tags and data maps alone support (R92).
 */
class TestpackModsCaseTest {
    @Test
    fun `CD-OPT-15 the case mods expects the compat line of each mod with a compat package, with its adapters`() {
        val script = File(System.getProperty("cd.projectDir"), "scripts/testpack/testpack.py").readText()
        val table = Regex("""^COMPAT_ADAPTERS = \{(.*?)^\}$""", setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL))
            .find(script)?.groupValues?.get(1)
        assertNotNull(table, "testpack.py has no COMPAT_ADAPTERS table")
        val expected = Regex(""""(\w+)": \(([^)]*)\)""").findAll(table!!).associate { entry ->
            entry.groupValues[1] to Regex(""""(\w+)"""").findAll(entry.groupValues[2]).map { it.groupValues[1] }.toSet()
        }
        assertEquals(CompatLines.reported(OptionalMods.ALL).associate { it.modId to it.adapters.toSet() }, expected)
    }
}
