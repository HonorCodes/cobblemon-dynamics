package codes.honor.cobblemondynamics.command

import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.stamina.RecoveryRule
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class StaminaReportTest {
    @Test
    fun `CD-UI-15 a healer at 40 percent reads 40_0, its state and the healer rule`() {
        val line = StaminaReport.line(40.0, WorkerStatus.HEALING, RecoveryRule.HEALER)
        assertEquals(StaminaReport.Line("40.0", "cobblemon_dynamics.state.healing", "cobblemon_dynamics.rule.healer"), line)
        assertEquals("cobblemon_dynamics.rule.worker", StaminaReport.line(40.0, WorkerStatus.WORKING, RecoveryRule.WORKER).ruleKey)
    }

    @Test
    fun `CD-UI-15 a Pokemon that is not a worker has its own state`() {
        assertEquals("cobblemon_dynamics.state.none", StaminaReport.line(100.0, null, RecoveryRule.WORKER).stateKey)
    }

    @Test
    fun `CD-UI-15 the value rounds down to one decimal, so it reads 100_0 only at full`() {
        assertEquals("99.9", StaminaReport.percentText(99.99))
        assertEquals("100.0", StaminaReport.percentText(100.0))
        assertEquals("37.5", StaminaReport.percentText(37.56))
        assertEquals("40.0", StaminaReport.percentText(39.999_999_999))
        assertEquals("0.0", StaminaReport.percentText(0.0))
    }

    @Test
    fun `CD-UI-15 every key of the stamina command has its English text`() {
        val lang = JsonParser.parseString(File("src/main/resources/assets/cobblemon_dynamics/lang/en_us.json").readText()).asJsonObject
        val states = WorkerStatus.entries.map { StaminaReport.stateKey(it) } + StaminaReport.stateKey(null)
        val rules = RecoveryRule.entries.map { StaminaReport.ruleKey(it) }
        val keys = states + rules + listOf(StaminaReport.LINE, StaminaReport.NOT_POKEMON)
        for (key in keys) assertTrue(lang.has(key), "en_us lacks $key")
        assertEquals("%s: %s%% Work Stamina, %s, %s", lang.get(StaminaReport.LINE).asString)
        assertEquals("healer rule", lang.get(StaminaReport.ruleKey(RecoveryRule.HEALER)).asString)
        assertEquals("worker rule", lang.get(StaminaReport.ruleKey(RecoveryRule.WORKER)).asString)
    }
}
