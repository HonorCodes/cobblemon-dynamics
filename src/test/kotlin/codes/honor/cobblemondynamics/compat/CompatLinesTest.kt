package codes.honor.cobblemondynamics.compat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CompatLinesTest {
    /**
     * CD-OPT-11: a compat line names the adapters of a compat package. Tags and data maps alone support a mod with no compat package,
     * so it has no adapter to name, and a line for it would read `[]`.
     */
    @Test
    fun `CD-OPT-11 only a mod with a compat package logs a compat line, and each such line names an adapter`() {
        val reported = CompatLines.reported(OptionalMods.ALL)
        val tagOnly = OptionalMods.ALL.filter { it.compat == null }
        assertTrue(tagOnly.any { it.modId == "aether" } && tagOnly.any { it.modId == "twilightforest" }, "tag-only mods: $tagOnly")
        assertTrue(tagOnly.none { it in reported }, "a tag-only mod logs a compat line: ${tagOnly.filter { it in reported }}")
        assertTrue(tagOnly.all { it.adapters.isEmpty() }, "a tag-only mod names an adapter: $tagOnly")
        assertEquals(OptionalMods.ALL.filter { it.compat != null }, reported)
        assertTrue(reported.all { it.adapters.isNotEmpty() }, "a compat line reads []: ${reported.filter { it.adapters.isEmpty() }}")
    }

    /** The stats hook of Apothic Enchanting is the adapter of `compat/apothic/`: the Muse's Quanta at a table (CD-MUS-07). */
    @Test
    fun `CD-OPT-11 the compat line of Apothic Enchanting names the Muse's Quanta`() {
        val apothic = OptionalMods.ALL.single { it.modId == "apothic_enchanting" }
        assertEquals(listOf("muse_quanta"), apothic.adapters)
    }
}
