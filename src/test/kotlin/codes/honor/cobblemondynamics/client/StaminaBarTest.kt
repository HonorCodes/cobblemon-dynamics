package codes.honor.cobblemondynamics.client

import codes.honor.cobblemondynamics.engine.WorkerStatus
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

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
