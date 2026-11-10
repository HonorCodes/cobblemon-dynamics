package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.Feedback
import com.cobblemon.mod.common.world.gamerules.CobblemonGameRules
import net.minecraft.world.level.GameRules
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.server.ServerStartedEvent

/** The world of every GameTest run: wild spawns, weather and night never touch a test, and every message is recorded. */
object TestWorld {
    fun register() {
        NeoForge.EVENT_BUS.addListener<ServerStartedEvent> { event ->
            val server = event.server
            val rules = server.gameRules
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server)
            rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server)
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server)
            rules.getRule(CobblemonGameRules.DO_POKEMON_SPAWNING).set(false, server)
            server.allLevels.forEach { it.dayTime = 6000 }
            // Fake players drop every message, so the tests read the record. Tests run side by side, so it stays on.
            Feedback.recording = true
        }
    }
}
