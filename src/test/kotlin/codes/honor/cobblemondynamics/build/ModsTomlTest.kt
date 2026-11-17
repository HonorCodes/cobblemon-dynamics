package codes.honor.cobblemondynamics.build

import codes.honor.cobblemondynamics.compat.OptionalMods
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.tomlj.Toml
import org.tomlj.TomlTable
import java.nio.file.Path

/** The optional mods of ADR-0016 in `neoforge.mods.toml`. [ModMetadataTest] reads the other entries. */
class ModsTomlTest {
    private val toml = Toml.parse(Path.of(System.getProperty("cd.modsToml"))).also {
        check(!it.hasErrors()) { it.errors().joinToString() }
    }

    private fun dependencies(): List<TomlTable> {
        val list = toml.getArray(listOf("dependencies", "cobblemon_dynamics")) ?: return emptyList()
        return (0 until list.size()).map { list.getTable(it) }
    }

    /** CD-PLAT-05 pins the range of AE2, which 1.0 declared before the table of ADR-0016 named it. */
    private val pinnedRanges = mapOf("ae2" to "[19.2,20)")

    @Test
    fun `CD-OPT-07 each optional mod that the code names is optional with the range star`() {
        val named = OptionalMods.ALL.filter { it.compat != null }
        assertTrue(named.isNotEmpty())
        val byId = dependencies().associateBy { it.getString("modId") }
        for (mod in named) {
            val entry = byId[mod.modId] ?: error("neoforge.mods.toml has no dependency entry for ${mod.modId}")
            assertEquals("optional", entry.getString("type"), mod.modId)
            assertEquals(pinnedRanges[mod.modId] ?: "*", entry.getString("versionRange"), mod.modId)
            assertEquals("BOTH", entry.getString("side"), mod.modId)
        }
    }
}
