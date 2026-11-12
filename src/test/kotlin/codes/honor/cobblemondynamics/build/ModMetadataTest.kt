package codes.honor.cobblemondynamics.build

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.tomlj.Toml
import org.tomlj.TomlTable
import java.nio.file.Path

class ModMetadataTest {
    private val toml = Toml.parse(Path.of(System.getProperty("cd.modsToml"))).also {
        check(!it.hasErrors()) { it.errors().joinToString() }
    }

    private fun mod(): TomlTable = toml.getArray("mods")!!.getTable(0)

    private fun dependency(modId: String): TomlTable? {
        val list = toml.getArray(listOf("dependencies", "cobblemon_dynamics")) ?: return null
        return (0 until list.size()).map { list.getTable(it) }.firstOrNull { it.getString("modId") == modId }
    }

    private fun requireDependency(modId: String): TomlTable =
        dependency(modId) ?: error("neoforge.mods.toml has no dependency entry for $modId")

    @Test
    fun `CD-PLAT-01 NeoForge is required from 21_1_219`() {
        val d = requireDependency("neoforge")
        assertEquals("required", d.getString("type"))
        assertEquals("[21.1.219,)", d.getString("versionRange"))
    }

    @Test
    fun `CD-PLAT-02 Cobblemon is required in the 1_8 line from 1_8_4`() {
        val d = requireDependency("cobblemon")
        assertEquals("required", d.getString("type"))
        assertEquals("[1.8.4,1.9)", d.getString("versionRange"))
    }

    @Test
    fun `CD-PLAT-03 the language loader is KotlinForForge from 5_10`() {
        assertEquals("kotlinforforge", toml.getString("modLoader"))
        assertEquals("[5.10,)", toml.getString("loaderVersion"))
        assertEquals("required", requireDependency("kotlinforforge").getString("type"))
    }

    @Test
    fun `CD-PLAT-05 Create and AE2 are optional with pinned ranges`() {
        val create = requireDependency("create")
        assertEquals("optional", create.getString("type"))
        assertEquals("[6.0.10,6.1)", create.getString("versionRange"))
        val ae2 = requireDependency("ae2")
        assertEquals("optional", ae2.getString("type"))
        assertEquals("[19.2,20)", ae2.getString("versionRange"))
    }

    @Test
    fun `CD-PLAT-06 every dependency applies on both sides`() {
        for (id in listOf("neoforge", "minecraft", "kotlinforforge", "cobblemon", "create", "ae2")) {
            assertEquals("BOTH", requireDependency(id).getString("side"), id)
        }
    }

    @Test
    fun `CD-PLAT-07 the mod id is cobblemon_dynamics`() {
        assertEquals("cobblemon_dynamics", mod().getString("modId"))
    }

    @Test
    fun `CD-PLAT-08 Cobbleworkers and Cobblebase are never required`() {
        for (id in listOf("cobbleworkers", "cobblebase")) {
            val d = dependency(id)
            if (d != null) assertNotEquals("required", d.getString("type"), id)
        }
    }

    @Test
    fun `CD-QWK-34 the claim mods are optional with any version`() {
        for (id in listOf("ftbchunks", "openpartiesandclaims")) {
            val d = requireDependency(id)
            assertEquals("optional", d.getString("type"), id)
            assertEquals("*", d.getString("versionRange"), id)
            assertEquals("BOTH", d.getString("side"), id)
        }
        assertNull(dependency("ftbteams"), "FTB Teams is a dependency of FTB Chunks, not of this mod")
    }

    @Test
    fun `CD-LND-14 minecolonies is optional with any version, and its libraries have no entry`() {
        val d = requireDependency("minecolonies")
        assertEquals("optional", d.getString("type"))
        assertEquals("*", d.getString("versionRange"))
        assertEquals("BOTH", d.getString("side"))
        for (library in listOf("structurize", "blockui", "domum_ornamentum", "multipiston")) {
            assertNull(dependency(library), "$library is a dependency of MineColonies, not of this mod")
        }
    }

    @Test
    fun `CD-MUS-07 Apothic Enchanting is optional with any version`() {
        val d = requireDependency("apothic_enchanting")
        assertEquals("optional", d.getString("type"))
        assertEquals("*", d.getString("versionRange"))
        assertEquals("BOTH", d.getString("side"))
    }

    @Test
    fun `CD-REL-05 the version reads MAJOR_MINOR_PATCH plus mc1_21_1`() {
        val version = mod().getString("version")!!
        assertTrue(Regex("""^\d+\.\d+\.\d+(-[0-9A-Za-z.]+)?\+mc1\.21\.1$""").matches(version), version)
    }

    @Test
    fun `CD-REL-06 the declared license is MPL-2_0`() {
        assertEquals("MPL-2.0", toml.getString("license"))
    }

    @Test
    fun `CD-ART-14 logo blur is off`() {
        assertFalse(mod().getBoolean("logoBlur")!!)
        assertEquals("logo.png", mod().getString("logoFile"))
    }
}
