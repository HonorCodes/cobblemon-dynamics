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
}
