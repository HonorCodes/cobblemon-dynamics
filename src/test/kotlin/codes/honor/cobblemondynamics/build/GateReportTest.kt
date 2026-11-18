package codes.honor.cobblemondynamics.build

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** The hand-written text of the 1.2 gate report, which `scripts/gate_report.py` keeps around its generated blocks. */
class GateReportTest {
    private val root = File(System.getProperty("cd.projectDir"))

    @Test
    fun `CD-BAL-17 the gate report names both balance flags as the row states them`() {
        val row = File(root, "docs/plans/2026-11-09-v1-spec-sheet.md").readLines().single { it.startsWith("| CD-BAL-17 |") }
        val flags = row.split(" | ")[1].substringAfter("that the approver accepts: ").removeSuffix(".").split(", and ")
        assertEquals(2, flags.size, "CD-BAL-17: the row states $flags, not two flags")
        val report = File(root, "docs/testing/v1.2-gate.md").readText()
        val section = report.substringAfter("\n## The balance flags\n", "").substringBefore("\n## ")
        for (flag in flags) {
            assertTrue(flag in section, "CD-BAL-17: the section \"The balance flags\" of the 1.2 gate report does not name $flag")
        }
    }
}
