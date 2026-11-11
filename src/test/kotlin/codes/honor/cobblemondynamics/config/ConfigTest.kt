package codes.honor.cobblemondynamics.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ConfigTest {
    @Test
    fun `CD-CFG-02 work radii accept 2 to 32 and default to 8 and 6`() {
        for (r in listOf(2, 32)) assertTrue(r in ConfigRanges.WORK_RADIUS)
        for (r in listOf(1, 33)) assertFalse(r in ConfigRanges.WORK_RADIUS)
        assertTrue(ServerConfig.isValid("workArea.horizontalRadius", 2))
        assertFalse(ServerConfig.isValid("workArea.horizontalRadius", 33))
        assertFalse(ServerConfig.isValid("workArea.verticalRadius", 1))
        assertEquals(8, EngineSettings.DEFAULT.horizontalRadius)
        assertEquals(6, EngineSettings.DEFAULT.verticalRadius)
    }

    @Test
    fun `CD-CFG-11 CD-CFG-12 CD-CFG-15 engine timing defaults`() {
        val s = EngineSettings.DEFAULT
        assertEquals(40, s.scanIntervalTicks)
        assertEquals(512, s.maxBlockChecksPerTick)
        assertEquals(600, s.travelTimeoutTicks)
        assertEquals(6000, s.retryWaitTicks)
        assertEquals(200, s.standbyMoveTicks)
    }

    @Test
    fun `CD-CFG-14 radii clamp to Cobblemon's pastureMaxWanderDistance`() {
        assertEquals(20, EngineSettings.effectiveRadius(configured = 32, maxWander = 20))
        assertEquals(19, EngineSettings.effectiveRadius(configured = 19, maxWander = 20))
    }

    @Test
    fun `CD-CFG-01 every documented key exists`() {
        for (k in CFG_01_KEYS) assertTrue(ServerConfig.hasKey(k), k)
    }

    @Test
    fun `CD-CFG-01 the test pack looks for the same keys in the file of a real server`() {
        val script = File(System.getProperty("cd.projectDir"), "scripts/testpack/testpack.py").readText()
        val tuple = Regex("""^SERVER_CONFIG_KEYS = \((.*?)\)$""", setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL))
            .find(script)?.groupValues?.get(1)
        assertNotNull(tuple, "testpack.py has no SERVER_CONFIG_KEYS tuple")
        assertEquals(CFG_01_KEYS, Regex(""""([^"]+)"""").findAll(tuple!!).map { it.groupValues[1] }.toList())
    }

    @Test
    fun `CD-CFG-06 the client config has no work sound option, because work is silent`() {
        assertFalse(ClientConfig.hasKey("effects.workSounds"))
    }

    @Test
    fun `CD-CFG-16 CD-CFG-17 CD-CFG-18 Quick Work keys, ranges and defaults`() {
        val s = EngineSettings.DEFAULT
        assertTrue(s.quickWorkEnabled)
        assertEquals(16, s.quickWorkTargetRange)
        assertEquals(16, s.quickWorkLeashRange)
        for (key in listOf("quickWork.targetRange", "quickWork.leashRange")) {
            assertTrue(ServerConfig.isValid(key, 2), key)
            assertTrue(ServerConfig.isValid(key, 24), key)
            assertFalse(ServerConfig.isValid(key, 1), key)
            assertFalse(ServerConfig.isValid(key, 25), key)
        }
    }

    private companion object {
        /** The keys of the spec's Config section, in the order that the config file writes them. The test pack looks for the same keys. */
        val CFG_01_KEYS = listOf(
            "workArea.horizontalRadius", "workArea.verticalRadius", "engine.scanIntervalTicks",
            "engine.maxBlockChecksPerTick", "engine.travelTimeoutTicks", "engine.retryWaitTicks",
            "engine.standbyMoveTicks", "engine.effectBroadcastRadius", "workMode.toggleCooldownTicks",
            "leash.pendingSelectionTicks", "quickWork.enabled", "quickWork.targetRange", "quickWork.leashRange",
            "stamina.ticksPerLevel", "stamina.healerRecoveryMinutesAtLevel1", "stamina.healerRecoveryMinutesAtLevel100",
            "stamina.flushIntervalTicks", "beds.speedFactors", "heal.cost", "heal.lowStaminaThreshold", "roles.disabled",
            "compat.cobbleworkers", "compat.cobblebase",
        )
    }
}
