package codes.honor.cobblemondynamics.build

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class CompatImportsTest {
    private val root = File(System.getProperty("cd.projectDir"))

    @Test
    fun `CD-QWK-33 only the compat package of a claim mod names it, and only behind isLoaded`() {
        val sources = File(root, "src/main").walk().filter { it.extension == "kt" || it.extension == "java" }
        for (file in sources) {
            val text = file.readText()
            val path = file.invariantSeparatorsPath
            if ("dev.ftb." in text) assertTrue("/compat/ftbchunks/" in path, "$path names FTB")
            if ("xaero.pac." in text) assertTrue("/compat/opac/" in path, "$path names Open Parties and Claims")
        }
        val mod = File(root, "src/main/kotlin/codes/honor/cobblemondynamics/CobblemonDynamics.kt").readText()
        assertTrue("if (ModList.get().isLoaded(\"ftbchunks\")) FtbChunksCompat.register()" in mod)
        assertTrue("if (ModList.get().isLoaded(\"openpartiesandclaims\")) OpacCompat.register()" in mod)
    }

    @Test
    fun `CD-LND-13 only compat-minecolonies names MineColonies, and only behind isLoaded`() {
        val sources = File(root, "src/main").walk().filter { it.extension == "kt" || it.extension == "java" }
        val naming = sources.filter { file -> file.readText().let { "com.minecolonies." in it || "com.ldtteam." in it } }.toList()
        for (file in naming) {
            val path = file.invariantSeparatorsPath
            assertTrue("/compat/minecolonies/" in path, "$path names MineColonies or a library of LDTTeam")
        }
        assertTrue(naming.isNotEmpty(), "no file names MineColonies, so compat/minecolonies/ is missing")
        val mod = File(root, "src/main/kotlin/codes/honor/cobblemondynamics/CobblemonDynamics.kt").readText()
        assertTrue("if (ModList.get().isLoaded(\"minecolonies\")) MineColoniesCompat.register()" in mod)
    }

    @Test
    fun `CD-MUS-07 only the compat-apothic packages name Apothic Enchanting, and only with the mod`() {
        val sources = File(root, "src/main").walk().filter { it.extension == "kt" || it.extension == "java" }
        val naming = sources.filter { "dev.shadowsoffire." in it.readText() }.toList()
        for (file in naming) {
            val path = file.invariantSeparatorsPath
            assertTrue("/compat/apothic/" in path, "$path names Apothic Enchanting")
        }
        assertTrue(naming.any { "/mixin/compat/apothic/" in it.invariantSeparatorsPath }, "mixin/compat/apothic/ has no stats hook")
        val plugin = File(root, "src/main/java/codes/honor/cobblemondynamics/mixin/MixinPlugin.java").readText()
        assertTrue("BASE + \"compat.apothic.\", \"apothic_enchanting\"" in plugin, "MixinPlugin applies the stats hook without the mod")
        val mod = File(root, "src/main/kotlin/codes/honor/cobblemondynamics/CobblemonDynamics.kt").readText()
        assertTrue("if (ModList.get().isLoaded(\"apothic_enchanting\")) ApothicCompat.register(modBus)" in mod)
    }
}
