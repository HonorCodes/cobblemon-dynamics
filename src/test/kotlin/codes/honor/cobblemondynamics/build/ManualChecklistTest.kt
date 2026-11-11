package codes.honor.cobblemondynamics.build

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ManualChecklistTest {
    private val root = File(System.getProperty("cd.projectDir"))
    private val checklist by lazy { File(root, "docs/testing/manual-checklist.md").readText() }

    /** Each requirement row of the spec sheet: its id, and the text of its acceptance test. */
    private val rows: Map<String, String> by lazy {
        File(root, SPEC).readLines().filter { it.startsWith("| CD-") }.associate { line ->
            val cells = line.trim().trim('|').split('|').map { it.trim() }
            cells[0] to cells[ACCEPTANCE_TEST]
        }
    }

    @Test
    fun `CD-REL-09 the manual checklist lists every Demo test of the spec sheet`() {
        val demos = rows.filter { (_, test) -> test.contains("Demo:") }.keys
        assertTrue(demos.isNotEmpty(), "no Demo rows in $SPEC")
        val missing = demos.filterNot { Regex("""\b$it\b""").containsMatchIn(checklist) }
        assertTrue(missing.isEmpty(), "Demo rows missing from the manual checklist: $missing")
    }

    @Test
    fun `CD-REL-09 every row id in the manual checklist is a row of the spec sheet`() {
        val named = ROW_ID.findAll(checklist).map { it.value }.toSet()
        val unknown = named - rows.keys
        assertTrue(unknown.isEmpty(), "the manual checklist names rows that the spec sheet lacks: $unknown")
    }

    private companion object {
        const val SPEC = "docs/plans/2026-11-09-v1-spec-sheet.md"
        const val ACCEPTANCE_TEST = 5
        val ROW_ID = Regex("""\bCD-[A-Z]+-\d{2}\b""")
    }
}
