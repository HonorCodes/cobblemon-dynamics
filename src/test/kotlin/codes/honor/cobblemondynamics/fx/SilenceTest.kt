package codes.honor.cobblemondynamics.fx

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The approver's play-test ruling (2026-11-10): all work is silent. A model's `cry` animation plays the Pokémon's cry, so no role file
 * and no fixed effect may name it, and the role loader rejects it.
 */
class SilenceTest {
    private val root = File(System.getProperty("cd.projectDir"))

    @Test
    fun `CD-FX-09 no role file in the repository names cry`() {
        val files = File(root, "src").walkTopDown().filter { it.extension == "json" && it.parentFile.name == "roles" }.toList()
        assertTrue(files.size > 10, "found only ${files.size} role files")
        for (file in files) {
            val animations = JsonParser.parseString(file.readText()).asJsonObject.getAsJsonObject("effect").getAsJsonArray("animations")
            assertFalse(animations.any { it.asString.equals("cry", ignoreCase = true) }, "${file.path} names cry")
        }
    }

    @Test
    fun `CD-FX-09 no fixed effect of the fx package names cry`() {
        assertFalse(HealEffect.FALLBACK_ANIMATIONS.any { it.equals("cry", ignoreCase = true) })
        val sources = File(root, "src/main/kotlin/codes/honor/cobblemondynamics/fx").listFiles { f -> f.extension == "kt" }.orEmpty()
        assertTrue(sources.isNotEmpty())
        for (file in sources) assertFalse(CRY.containsMatchIn(file.readText()), "${file.name} names the cry animation")
    }

    private companion object {
        val CRY = Regex("\"cry\"", RegexOption.IGNORE_CASE)
    }
}
