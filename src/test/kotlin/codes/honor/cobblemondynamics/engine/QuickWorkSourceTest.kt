package codes.honor.cobblemondynamics.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** The Inspect parts of the Quick Work cost rows, read from the work engine's source (ADR-0013, decision 8). */
class QuickWorkSourceTest {
    private val source by lazy { File(System.getProperty("cd.projectDir"), ENGINE).readText() }

    /** The body of the engine's function [name], from its opening brace to the brace that closes it. */
    private fun body(name: String): String {
        val start = Regex("""\bfun $name\(""").find(source)?.range?.first ?: throw AssertionError("$ENGINE has no function $name")
        val open = source.indexOf('{', start)
        var depth = 0
        for (i in open until source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return source.substring(open, i + 1)
            }
        }
        throw AssertionError("the body of $name in $ENGINE does not end")
    }

    @Test
    fun `CD-QWK-23 an order's tick runs a worker's steps on its own claim and owner, with no scan, assignment or block loop`() {
        val tick = body("tickOrder")
        val missing = OWN_STEPS.filterNot { it in tick }
        assertTrue(missing.isEmpty(), "tickOrder does not run $missing")
        val found = SCAN_AND_SEARCH.filter { Regex("""\b""" + Regex.escape(it)).containsMatchIn(tick) }
        assertTrue(found.isEmpty(), "tickOrder runs $found")
    }

    /**
     * With no order, `quick` is empty, so every line of `tickLevel` that names the orders or an order's tick is the one check that an
     * order exists, or a loop over `quick`. The Analysis part of CD-QWK-24, three runs of the reference world before Quick Work and
     * three with it, is recorded in the body of pull request #22.
     */
    @Test
    fun `CD-QWK-24 with no order the level tick runs no Quick Work step beyond the one check that an order exists`() {
        val lines = body("tickLevel").lines().map { it.trim() }
        assertEquals(1, lines.count { CHECK in it }, "tickLevel does not check once that an order exists, with `$CHECK`")
        val quickWork = lines.filter { QUICK_WORK.containsMatchIn(it) && !it.startsWith("//") }
        val unguarded = quickWork.filterNot { it.startsWith("val quick = $CHECK") || it.startsWith("for (order in quick)") }
        assertTrue(unguarded.isEmpty(), "tickLevel runs Quick Work outside its check that an order exists: $unguarded")
    }

    private companion object {
        const val ENGINE = "src/main/kotlin/codes/honor/cobblemondynamics/engine/WorkEngine.kt"
        const val CHECK = "if (orders.isEmpty()) emptyList() else"

        /** CD-QWK-23: the end checks of the order on its owner and its own claim, then the steps of a pasture worker's tick. */
        val OWN_STEPS = listOf("playerList.getPlayer(order.owner)", "claims.claimOf(worker.pokemonId)", "tickWorker(", "tickStamina(")

        /** The scan pass, the assignment and the block loops of a pasture's tick, which an order's tick never runs (CD-QWK-23). */
        val SCAN_AND_SEARCH = listOf("scan(", "assign(", "assignHeals(", "TargetScan", "stepBlocks(", "betweenClosed", "MutableBlockPos")

        /** The names that mark Quick Work in the level tick: the orders and aims of the engine, and the tick of one order. */
        val QUICK_WORK = Regex("""\b(orders|aims|tickOrder|QuickRuntime)\b""")
    }
}
