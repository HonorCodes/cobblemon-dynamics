package codes.honor.cobblemondynamics.compat

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The claim questions of the packages of the three claim mods, read from their sources with the comments removed: the silent break
 * question of a pasture (CD-HRV-21), and the player that a question about a player passes on (CD-QWK-31).
 */
class ClaimQuestionsTest {
    private val root = File(System.getProperty("cd.projectDir"))

    private fun source(path: String): String = File(root, path).readText().replace(COMMENT, "")

    /** Each function of [source] by name, from its `fun` to the next `fun` or the end of the source. */
    private fun functions(source: String): Map<String, String> {
        val starts = FUN.findAll(source).toList()
        return starts.withIndex().associate { (index, match) ->
            match.groupValues[1] to source.substring(match.range.first, starts.getOrNull(index + 1)?.range?.first ?: source.length)
        }
    }

    /** [start] and each function of [functions] that it calls with no receiver, directly or through another one. */
    private fun reach(functions: Map<String, String>, start: String): Set<String> {
        val seen = mutableSetOf(start)
        val queue = ArrayDeque(seen)
        while (queue.isNotEmpty()) {
            for (call in CALL.findAll(functions.getValue(queue.removeFirst()))) {
                val name = call.groupValues[1]
                if (name in functions && seen.add(name)) queue.add(name)
            }
        }
        return seen
    }

    @Test
    fun `CD-HRV-21 the break question of a pasture asks no player check, sends no message and posts no event`() {
        val found = mutableListOf<String>()
        for (path in CHECKS) {
            val functions = functions(source(path))
            assertTrue("ownerMayBreak" in functions, "CD-HRV-21: $path has no ownerMayBreak")
            for (name in reach(functions, "ownerMayBreak")) {
                found += SILENT.filter { it.containsMatchIn(functions.getValue(name)) }.map { "$path: $name matches ${it.pattern}" }
            }
        }
        assertTrue(found.isEmpty(), "CD-HRV-21: $found")
    }

    @Test
    fun `CD-QWK-31 a claim question about a player gets the owner's own ServerPlayer from the player list and passes it on`() {
        val problems = mutableListOf<String>()
        val engine = source(ENGINE)
        val lookups = LOOKUP.findAll(engine).map { it.groupValues[1] }.toList()
        if (lookups.isEmpty()) problems += "the engine looks up no player"
        problems += lookups.filter { it != "playerList" }.map { "the engine looks up a player through $it" }
        problems += CLAIM_CALL.findAll(engine).map { it.groupValues[2] }.filter { it != "owner" }.map { "the engine asks about $it" }
        for (path in CHECKS + LAND_CLAIMS + ENGINE) {
            val text = source(path)
            problems += BUILDS.filter { it.containsMatchIn(text) }.map { "$path builds a player: ${it.pattern}" }
        }
        for (path in CHECKS + LAND_CLAIMS) {
            val text = source(path)
            if (LOOKUP.containsMatchIn(text)) problems += "$path looks up a player"
            val functions = functions(text)
            for (name in listOf("mayUse", "mayBreak")) {
                if (!PASSES_OWNER.containsMatchIn(functions[name].orEmpty())) problems += "$path: $name passes on no owner"
            }
        }
        assertTrue(problems.isEmpty(), "CD-QWK-31: $problems")
    }

    private companion object {
        const val COMPAT = "src/main/kotlin/codes/honor/cobblemondynamics/compat"
        const val ENGINE = "src/main/kotlin/codes/honor/cobblemondynamics/engine/WorkEngine.kt"
        const val LAND_CLAIMS = "$COMPAT/LandClaims.kt"
        val CHECKS = listOf(
            "$COMPAT/ftbchunks/FtbChunksCompat.kt",
            "$COMPAT/opac/OpacCompat.kt",
            "$COMPAT/minecolonies/MineColoniesCompat.kt",
        )
        val COMMENT = Regex("""/\*.*?\*/|//[^\n]*""", RegexOption.DOT_MATCHES_ALL)
        val FUN = Regex("""\bfun\s+(\w+)\s*\(""")
        val CALL = Regex("""(?<![\w.:])(\w+)\(""")

        /** The calls that ask a claim mod as a player does, which can show text or count an attempt, a message and an event. */
        val SILENT = listOf(
            Regex("""\bshouldPreventInteraction\b"""),
            Regex("""\bonBlockInteraction\b"""),
            Regex("""\bsendSystemMessage\b"""),
            Regex("""\bdisplayClientMessage\b"""),
            Regex("""\bEVENT_BUS\b"""),
            Regex("""\.post\("""),
        )
        val BUILDS = listOf(Regex("""\bFakePlayer(Factory)?\b"""), Regex("""\bGameProfile\("""), Regex("""\bServerPlayer\("""))
        val LOOKUP = Regex("""(\w+)(?:\.|::)getPlayer(?:ByUUID)?\b""")
        val CLAIM_CALL = Regex("""\bLandClaims\.(mayUse|mayBreak)\((\w+)""")
        val PASSES_OWNER = Regex("""\(owner[,)]""")
    }
}
