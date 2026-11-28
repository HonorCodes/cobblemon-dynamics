package codes.honor.cobblemondynamics.config

import com.electronwill.nightconfig.core.UnmodifiableConfig
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
        for (r in listOf(4, 32)) assertTrue(r in ConfigRanges.MAX_HORIZONTAL_RADIUS)
        for (r in listOf(3, 33)) assertFalse(r in ConfigRanges.MAX_HORIZONTAL_RADIUS)
        assertTrue(ServerConfig.isValid("workArea.horizontalRadius", 2))
        assertFalse(ServerConfig.isValid("workArea.horizontalRadius", 33))
        assertTrue(ServerConfig.isValid("workArea.maxHorizontalRadius", 4))
        assertTrue(ServerConfig.isValid("workArea.maxHorizontalRadius", 32))
        assertFalse(ServerConfig.isValid("workArea.maxHorizontalRadius", 3))
        assertFalse(ServerConfig.isValid("workArea.maxHorizontalRadius", 33))
        assertFalse(ServerConfig.isValid("workArea.verticalRadius", 1))
        assertEquals(8, EngineSettings.DEFAULT.horizontalRadius)
        assertEquals(16, EngineSettings.DEFAULT.maxHorizontalRadius)
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

    @Test
    fun `CD-CFG-19 logistics workers per container accept 1 to 16 and default to 5`() {
        assertEquals(5, EngineSettings.DEFAULT.maxWorkersPerContainer)
        for (n in listOf(1, 16)) assertTrue(ServerConfig.isValid(WORKERS_PER_CONTAINER, n), "$n is refused")
        for (n in listOf(0, 17)) assertFalse(ServerConfig.isValid(WORKERS_PER_CONTAINER, n), "$n is accepted")
    }

    @Test
    fun `CD-CFG-20 the Experience Collector's capacity accepts 1,000 to 1,000,000 and defaults to 100,000`() {
        assertEquals(100_000, EngineSettings.DEFAULT.experienceCollectorCapacity)
        for (n in listOf(1_000, 100_000, 1_000_000)) assertTrue(ServerConfig.isValid(COLLECTOR_CAPACITY, n), "$n is refused")
        for (n in listOf(999, 1_000_001)) assertFalse(ServerConfig.isValid(COLLECTOR_CAPACITY, n), "$n is accepted")
    }

    @Test
    fun `CD-QWK-35 CD-LND-15 the server config holds the keys of CD-CFG-01 and no claim key`() =
        assertEquals(CFG_01_KEYS.toSet(), keys(ServerConfig.SPEC.spec).toSet())

    @Test
    fun `CD-PRG-01 progression keys defaults and exact bounds`() {
        val s = EngineSettings.DEFAULT
        assertEquals(1.15, s.shinyMultiplier, 1e-9)
        assertEquals(1.25, s.alphaMultiplier, 1e-9)
        assertEquals(1.35, s.majesticMultiplier, 1e-9)
        assertEquals(1.20, s.moveMasteryMultiplier, 1e-9)
        assertEquals(1.15, s.mateMultiplier, 1e-9)
        assertTrue(s.workXpEnabled)
        assertTrue(s.friendshipEnabled)
        assertEquals(0.12, s.baseSlackChance, 1e-9)
        assertEquals(1_200, s.slackCheckTicks)
        assertEquals(300, s.slackDurationTicks)

        val multipliers = listOf(
            "progression.shinyMultiplier",
            "progression.alphaMultiplier",
            "progression.majesticMultiplier",
            "progression.moveMasteryMultiplier",
            "progression.mateMultiplier",
        )
        for (key in multipliers) {
            assertTrue(ServerConfig.hasKey(key), key)
            assertTrue(ServerConfig.isValid(key, 1.0), "$key at 1.0")
            assertTrue(ServerConfig.isValid(key, 5.0), "$key at 5.0")
            assertFalse(ServerConfig.isValid(key, 0.99), "$key at 0.99")
            assertFalse(ServerConfig.isValid(key, 5.01), "$key at 5.01")
        }
        for (key in listOf("progression.workXpEnabled", "progression.friendshipEnabled")) {
            assertTrue(ServerConfig.hasKey(key), key)
            assertTrue(ServerConfig.isValid(key, true), key)
            assertTrue(ServerConfig.isValid(key, false), key)
        }
        assertTrue(ServerConfig.isValid("progression.baseSlackChance", 0.0))
        assertTrue(ServerConfig.isValid("progression.baseSlackChance", 1.0))
        assertFalse(ServerConfig.isValid("progression.baseSlackChance", -0.01))
        assertFalse(ServerConfig.isValid("progression.baseSlackChance", 1.01))

        assertTrue(ServerConfig.isValid("progression.slackCheckTicks", 100))
        assertTrue(ServerConfig.isValid("progression.slackCheckTicks", 72_000))
        assertFalse(ServerConfig.isValid("progression.slackCheckTicks", 99))
        assertFalse(ServerConfig.isValid("progression.slackCheckTicks", 72_001))

        assertTrue(ServerConfig.isValid("progression.slackDurationTicks", 20))
        assertTrue(ServerConfig.isValid("progression.slackDurationTicks", 6_000))
        assertFalse(ServerConfig.isValid("progression.slackDurationTicks", 19))
        assertFalse(ServerConfig.isValid("progression.slackDurationTicks", 6_001))
    }

    /** The full path of every value, as PlayerGuideTest reads it. */
    private fun keys(config: UnmodifiableConfig, prefix: String = ""): List<String> = config.valueMap().flatMap { (name, value) ->
        val path = if (prefix.isEmpty()) name else "$prefix.$name"
        if (value is UnmodifiableConfig) keys(value, path) else listOf(path)
    }

    private companion object {
        const val WORKERS_PER_CONTAINER = "logistics.maxWorkersPerContainer"
        const val COLLECTOR_CAPACITY = "experienceCollector.capacity"

        /** The keys of the spec's Config section, in the order that the config file writes them. The test pack looks for the same keys. */
        val CFG_01_KEYS = listOf(
            "workArea.horizontalRadius", "workArea.maxHorizontalRadius", "workArea.verticalRadius", "engine.scanIntervalTicks",
            "engine.maxBlockChecksPerTick", "engine.travelTimeoutTicks", "engine.retryWaitTicks",
            "engine.standbyMoveTicks", "engine.effectBroadcastRadius", "workMode.toggleCooldownTicks",
            "leash.pendingSelectionTicks", WORKERS_PER_CONTAINER, "quickWork.enabled", "quickWork.targetRange", "quickWork.leashRange",
            "stamina.ticksPerLevel", "stamina.healerRecoveryMinutesAtLevel1", "stamina.healerRecoveryMinutesAtLevel100",
            "stamina.flushIntervalTicks", "beds.speedFactors", "heal.cost", "heal.lowStaminaThreshold", "roles.disabled",
            "compat.cobbleworkers", "compat.cobblebase", COLLECTOR_CAPACITY,
            "progression.shinyMultiplier", "progression.alphaMultiplier", "progression.majesticMultiplier",
            "progression.moveMasteryMultiplier", "progression.mateMultiplier", "progression.workXpEnabled",
            "progression.friendshipEnabled", "progression.baseSlackChance", "progression.slackCheckTicks",
            "progression.slackDurationTicks",
        )
    }
}
