package codes.honor.cobblemondynamics.build

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import javax.imageio.ImageIO

/** The art rows that need no game and no reference jar. `scripts/art.py check` runs the full checks. */
class ArtFilesTest {
    private val root = File(System.getProperty("cd.projectDir"))
    private val assets = File(root, "src/main/resources/assets/cobblemon_dynamics")
    private val art = File(root, "art")
    private val licenseWords = Regex("licen[cs]e|copyright|all rights reserved|creative commons|\\bcc[- ]by", RegexOption.IGNORE_CASE)

    private fun shipped(kind: String): List<File> = File(assets, kind).walk().filter { it.isFile }.toList()

    /** The source of a shipped texture, mcmeta or model. */
    private fun sourceOf(file: File): File {
        val path = file.relativeTo(assets).invariantSeparatorsPath
        return File(
            art,
            when {
                path.endsWith(".png") -> path.removeSuffix(".png") + ".pixels"
                path.endsWith(".png.mcmeta") -> path.removeSuffix(".png.mcmeta") + ".pixels.mcmeta"
                else -> path
            },
        )
    }

    @Test
    fun `CD-ART-06 every block and item texture is 16 x 16`() {
        val textures = listOf("block", "item").flatMap { shipped("textures/$it") }.filter { it.extension == "png" }
        assertTrue(textures.isNotEmpty(), "no block or item texture")
        for (png in textures) {
            val image = ImageIO.read(png)
            assertEquals(16 to 16, image.width to image.height, png.path)
        }
    }

    @Test
    fun `CD-ART-08 every shipped texture and model has its source under art`() {
        val missing = (shipped("textures") + shipped("models")).filterNot { sourceOf(it).isFile }.map { it.relativeTo(root).path }
        assertTrue(missing.isEmpty(), "no source under art/ for $missing")
    }

    @Test
    fun `CD-ART-13 LICENSE is MPL-2_0 and no asset states a license of its own`() {
        assertTrue(File(root, "LICENSE").readText().contains("Mozilla Public License Version 2.0"))
        val texts = (art.walk() + assets.walk()).filter { it.isFile && it.extension in TEXT_EXTENSIONS }
        val stated = texts.filter { licenseWords.containsMatchIn(it.readText()) }.map { it.relativeTo(root).path }.toList()
        assertTrue(stated.isEmpty(), "asset files that state a license: $stated")
    }

    @Test
    fun `CD-ART-11 only a public release requires the approver's approval of the art`() {
        assertFalse(File(root, ".github/workflows/build.yml").readText().contains("art.py check --release"))
    }

    private companion object {
        val TEXT_EXTENSIONS = setOf("json", "mcmeta", "pixels", "md", "txt")
    }
}
