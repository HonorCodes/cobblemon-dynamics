package codes.honor.cobblemondynamics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LoadLogTest {
    @Test
    fun `CD-CMP-10 a line logs once per load, and again after the next load`() {
        val lines = mutableListOf<String>()
        val log = LoadLog(lines::add)
        log.info("Roles: 16 loaded, 16 active, 0 disabled []")
        log.info("Roles: 16 loaded, 16 active, 0 disabled []")
        assertEquals(1, lines.size, "a rebuild with no change logs nothing")
        log.info("Roles: 16 loaded, 15 active, 1 disabled [cobblemon_dynamics:muse (roles.disabled)]")
        assertEquals(2, lines.size, "a changed line logs")
        log.newLoad()
        log.info("Roles: 16 loaded, 16 active, 0 disabled []")
        assertEquals(3, lines.size, "a new load logs its line again")
    }

    @Test
    fun `CD-CRP-05 each farming line logs once per load of the tags`() {
        val lines = mutableListOf<String>()
        val log = LoadLog(lines::add)
        val cultivator = LoadLines.farming("cultivator", mapOf("growth" to 3), emptyList())
        val harvester = LoadLines.farming("harvester", mapOf("crop" to 2, "never" to 1), emptyList())
        repeat(2) {
            log.info(cultivator)
            log.info(harvester)
        }
        assertEquals(listOf(cultivator, harvester), lines)
        log.newLoad()
        log.info(cultivator)
        assertEquals(listOf(cultivator, harvester, cultivator), lines, "a new load of the tags logs its lines again")
    }
}
