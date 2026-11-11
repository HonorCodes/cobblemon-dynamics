package codes.honor.cobblemondynamics.client

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import javax.imageio.ImageIO

class WorkModeSpritesTest {
    private val textures = File(System.getProperty("cd.projectDir"), "src/main/resources/assets/cobblemon_dynamics/textures")

    @Test
    fun `CD-ART-04 the button shows a sprite for on, off, disabled and hover`() {
        assertEquals(WorkModeSprites.ON, WorkModeSprites.of(workMode = true, active = true, hovered = false))
        assertEquals(WorkModeSprites.OFF, WorkModeSprites.of(workMode = false, active = true, hovered = false))
        assertEquals(WorkModeSprites.HOVER, WorkModeSprites.of(workMode = true, active = true, hovered = true))
        assertEquals(WorkModeSprites.DISABLED, WorkModeSprites.of(workMode = true, active = false, hovered = true))
        assertEquals(4, setOf(WorkModeSprites.ON, WorkModeSprites.OFF, WorkModeSprites.DISABLED, WorkModeSprites.HOVER).size)
    }

    @Test
    fun `CD-ART-04 the jar holds the four button sprites and the bar sprites`() {
        val sprites = listOf("on", "off", "disabled", "hover").map { "gui/sprites/work_mode/$it.png" } +
            listOf("panel_bar", "panel_fill", "head_bar", "head_fill").map { "gui/stamina_bar/$it.png" }
        for (sprite in sprites) assertTrue(File(textures, sprite).isFile, "missing $sprite")
    }

    /** The GUI atlas finds a sprite by its id under `textures/gui/sprites/`, and draws it nine-sliced at the button's size. */
    @Test
    fun `CD-ART-04 each button sprite is the button's size and nine-sliced`() {
        val button = PanelLayout.BUTTON
        for (sprite in listOf(WorkModeSprites.ON, WorkModeSprites.OFF, WorkModeSprites.DISABLED, WorkModeSprites.HOVER)) {
            val png = File(textures, "gui/sprites/${sprite.path}.png")
            val image = ImageIO.read(png)
            assertEquals(button.width to button.height, image.width to image.height, png.name)
            val scaling = File(png.path + ".mcmeta").reader(Charsets.UTF_8).use { JsonParser.parseReader(it) }
                .asJsonObject.getAsJsonObject("gui").getAsJsonObject("scaling")
            assertEquals("nine_slice", scaling.get("type").asString, png.name)
            assertEquals(button.width to button.height, scaling.get("width").asInt to scaling.get("height").asInt, png.name)
            assertTrue(2 * scaling.get("border").asInt < button.height, "${png.name}: the border leaves no centre")
        }
    }
}
