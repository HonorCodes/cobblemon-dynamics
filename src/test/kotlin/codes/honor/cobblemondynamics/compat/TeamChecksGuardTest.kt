package codes.honor.cobblemondynamics.compat

import codes.honor.cobblemondynamics.OnceLogger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class TeamChecksGuardTest {
    private val lines = mutableListOf<String>()
    private val guard = TeamChecksGuard(OnceLogger(lines::add))
    private val a = UUID.randomUUID()
    private val b = UUID.randomUUID()

    @Test
    fun `CD-TEAM-11 absent providers treat distinct players as non-teammates and same UUID as true`() {
        assertFalse(guard.anyTeammate(a, b, emptyList<Pair<String, Int>>()) { error("no provider") })
        assertTrue(guard.anyTeammate(a, a, emptyList<Pair<String, Int>>()) { error("same player") })
        assertTrue(lines.isEmpty())
    }

    @Test
    fun `CD-TEAM-11 throwing team providers return false and log once per mod and error class`() {
        val errors = listOf(RuntimeException("boom"), NoSuchMethodError("arePlayersInSameTeam"), NoClassDefFoundError("dev/ftb/Teams"))
        for (error in errors) {
            assertFalse(guard.anyTeammate(a, b, listOf("ftbteams" to 1)) { throw error })
        }
        assertFalse(guard.anyTeammate(a, b, listOf("ftbteams" to 1)) { throw RuntimeException("again") })
        assertEquals(3, lines.size, "$lines")
        assertFalse(guard.anyTeammate(a, b, listOf("openpartiesandclaims" to 2)) { throw RuntimeException("opac") })
        assertEquals(4, lines.size, "$lines")

        val mixed = listOf("ftbteams" to 1, "openpartiesandclaims" to 2)
        assertTrue(guard.anyTeammate(a, b, mixed) { if (it == 1) throw NoSuchMethodError("arePlayersInSameTeam") else true })
        assertEquals(4, lines.size, "repeated error class did not log again: $lines")
    }
}
