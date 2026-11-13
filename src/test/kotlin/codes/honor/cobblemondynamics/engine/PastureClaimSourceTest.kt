package codes.honor.cobblemondynamics.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** The Inspect part of CD-LND-12, read from the work engine's source: where the engine asks the pasture check. */
class PastureClaimSourceTest {
    /** The engine's source without its comments. */
    private val source by lazy {
        File(System.getProperty("cd.projectDir"), ENGINE).readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""//[^\n]*"""), "")
    }

    /** Each function of the engine by name, with its text from its parameters to the next function. Overloads share a name. */
    private val functions: List<Pair<String, String>> by lazy {
        val starts = Regex("""\bfun (\w+)\(""").findAll(source).toList()
        starts.mapIndexed { i, match ->
            val end = starts.getOrNull(i + 1)?.range?.first ?: source.length
            match.groupValues[1] to source.substring(match.range.last + 1, end)
        }
    }

    private fun callers(call: String): Set<String> = functions.filter { (_, text) -> call in text }.mapTo(sortedSetOf()) { it.first }

    @Test
    fun `CD-LND-12 the engine asks the pasture check only in the poll, the assignment after a scan, the choice of a bed and a harvest`() {
        val askers = setOf("assign", "landRefusal", "mayChange", "nearestFreeBed", "placerMayUse")
        assertEquals(askers, callers(PASTURE_CHECK), "who asks $PASTURE_CHECK")
        // CD-HRV-13: the harvest asks through its context, once for each block that it changes.
        assertEquals(setOf("context"), callers("mayChange("), "who calls mayChange")
        // The two helpers of the poll: the binding's target and destination (CD-LND-03), and each claim (CD-LND-02).
        for (helper in listOf("landRefusal(", "placerMayUse(")) assertEquals(setOf("poll"), callers(helper), "who calls $helper")
        for (name in listOf("tickWorker", "tickLevel")) {
            val text = functions.filter { it.first == name }.joinToString("") { it.second }
            assertTrue(text.isNotEmpty(), "$ENGINE has no function $name")
            val asks = listOf(PASTURE_CHECK, "landRefusal(", "placerMayUse(").filter { it in text }
            assertTrue(asks.isEmpty(), "$name asks the pasture check through $asks")
        }
    }

    private companion object {
        const val ENGINE = "src/main/kotlin/codes/honor/cobblemondynamics/engine/WorkEngine.kt"
        const val PASTURE_CHECK = "LandClaims.ownerMayUse("
    }
}
