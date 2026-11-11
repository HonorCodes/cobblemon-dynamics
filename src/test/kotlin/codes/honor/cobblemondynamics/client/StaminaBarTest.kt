package codes.honor.cobblemondynamics.client

import codes.honor.cobblemondynamics.engine.WorkerStatus
import com.google.gson.JsonParser
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.File
import javax.imageio.ImageIO

class StaminaBarTest {
    private val lang = File(System.getProperty("cd.projectDir"), "src/main/resources/assets/cobblemon_dynamics/lang/en_us.json")
        .reader(Charsets.UTF_8).use { JsonParser.parseReader(it).asJsonObject }

    /** The text that Minecraft shows for [line]: its `%s` placeholders filled in order and `%%` as a percent sign. */
    private fun text(line: StaminaBar.Line): String = String.format(lang[line.key].asString, *line.args.toTypedArray())

    @Test
    fun `the fill is the floor of the percentage of the inner width, and at least 1 pixel above 0`() {
        assertEquals(0, StaminaBar.fillWidth(0, 48))
        assertEquals(1, StaminaBar.fillWidth(1, 48))
        assertEquals(20, StaminaBar.fillWidth(42, 48))
        assertEquals(47, StaminaBar.fillWidth(99, 48))
        assertEquals(48, StaminaBar.fillWidth(100, 48))
        assertEquals(48, StaminaBar.fillWidth(150, 48))
        assertEquals(0, StaminaBar.fillWidth(-5, 48))
    }

    @Test
    fun `the fill is green from 50, yellow from 25 and red below`() {
        assertEquals(StaminaBar.GREEN, StaminaBar.color(100))
        assertEquals(StaminaBar.GREEN, StaminaBar.color(50))
        assertEquals(StaminaBar.YELLOW, StaminaBar.color(49))
        assertEquals(StaminaBar.YELLOW, StaminaBar.color(25))
        assertEquals(StaminaBar.RED, StaminaBar.color(24))
        assertEquals(StaminaBar.RED, StaminaBar.color(0))
        assertEquals(3, setOf(StaminaBar.GREEN, StaminaBar.YELLOW, StaminaBar.RED).size)
    }

    /** The head bars draw the parts as coplanar quads, which must not overlap, or they flicker. */
    @Test
    fun `a bar's parts tile it without overlap, with the fill at its colour`() {
        val bar = Rect(-10, -2, 30, 4)
        for (percent in listOf(0, 1, 42, 99, 100)) {
            val parts = StaminaBar.parts(bar, percent)
            val area = parts.sumOf { it.first.width * it.first.height }
            assertEquals(bar.width * bar.height, area, "the parts at $percent% must cover the bar")
            for ((i, a) in parts.withIndex()) {
                assertEquals(true, bar.contains(a.first), "${a.first} leaves the bar")
                for (b in parts.drop(i + 1)) assertEquals(false, a.first.overlaps(b.first), "${a.first} overlaps ${b.first}")
            }
            val fill = parts.filter { it.second == StaminaBar.color(percent) }.sumOf { it.first.width }
            assertEquals(StaminaBar.fillWidth(percent, bar.width - 2), fill, "the fill width at $percent%")
        }
    }

    @Test
    fun `the fill is the left fill width of the bar's inside, and nothing at 0 percent`() {
        val bar = Rect(-10, -2, 30, 4)
        assertEquals(Rect(-9, -1, 28, 2), StaminaBar.inner(bar))
        assertNull(StaminaBar.fill(bar, 0))
        assertEquals(Rect(-9, -1, 1, 2), StaminaBar.fill(bar, 1))
        assertEquals(Rect(-9, -1, 11, 2), StaminaBar.fill(bar, 42))
        assertEquals(Rect(-9, -1, 28, 2), StaminaBar.fill(bar, 100))
    }

    /** The bars draw their sprites one texture pixel per GUI or name-tag pixel. */
    @Test
    fun `CD-ART-04 each bar sprite is the size of the bar that draws it`() {
        val assets = File(System.getProperty("cd.projectDir"), "src/main/resources/assets/cobblemon_dynamics")

        fun size(texture: ResourceLocation) = ImageIO.read(File(assets, texture.path)).let { it.width to it.height }
        val panel = PanelLayout.rowParts(0, 0).bar
        val head = HeadBarRange.BAR
        assertEquals(panel.width to panel.height, size(StaminaBar.PANEL_BAR))
        assertEquals(StaminaBar.inner(panel).let { it.width to it.height }, size(StaminaBar.PANEL_FILL))
        assertEquals(head.width to head.height, size(StaminaBar.HEAD_BAR))
        assertEquals(StaminaBar.inner(head).let { it.width to it.height }, size(StaminaBar.HEAD_FILL))
    }

    @Test
    fun `CD-UI-10 CD-UI-11 the tooltip of a bar at 42 percent shows 42% Work Stamina and the state name`() {
        val lines = StaminaBar.tooltip(42, WorkerStatus.WORKING)
        assertEquals(listOf("42% Work Stamina", "Working"), lines.map(::text))
        assertEquals("Resting in Place", text(StaminaBar.tooltip(0, WorkerStatus.RESTING_IN_PLACE)[1]))
        for (status in WorkerStatus.entries) {
            val key = StaminaBar.tooltip(100, status)[1].key
            assertEquals(true, lang.has(key), "no lang value for $key")
        }
    }
}
