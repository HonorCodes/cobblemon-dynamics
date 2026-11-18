package codes.honor.cobblemondynamics.handler.farming

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** The sparkles of a growth use, read from the source of the Cultivator's handler. */
class CultivatorSparklesTest {
    private val source = File(System.getProperty("cd.projectDir"), HANDLER).readText()

    @Test
    fun `CD-CUL-07 a growth use sends 15 happy villager particles through sendParticles`() {
        val calls = SEND.findAll(source).map { call -> call.groupValues[1].split(",").map { it.trim() } }.toList()
        assertTrue(calls.isNotEmpty(), "CD-CUL-07: the Cultivator's handler calls no sendParticles")
        for (arguments in calls) {
            assertEquals("ParticleTypes.HAPPY_VILLAGER", arguments.first(), "CD-CUL-07: the handler sends ${arguments.first()}")
            val count = arguments[4]
            val value = count.toIntOrNull() ?: Regex("""const val $count = (\d+)""").find(source)?.groupValues?.get(1)?.toInt()
            assertEquals(15, value, "CD-CUL-07: the count $count of the particles is $value")
        }
    }

    private companion object {
        const val HANDLER = "src/main/kotlin/codes/honor/cobblemondynamics/handler/farming/CultivatorHandler.kt"
        val SEND = Regex("""\bsendParticles\(([^)]*)\)""")
    }
}
