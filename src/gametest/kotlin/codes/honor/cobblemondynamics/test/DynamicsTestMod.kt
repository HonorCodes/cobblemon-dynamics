package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.Feedback
import codes.honor.cobblemondynamics.handler.HandlerRegistry
import com.cobblemon.mod.common.world.gamerules.CobblemonGameRules
import net.minecraft.world.level.GameRules
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent

const val TEST_MOD_ID = "cobblemon_dynamics_test"

/**
 * Set in an entity's NeoForge data by the last listener of an entity click. A click that it marks was not cancelled, so the entity's
 * own interaction ran after it.
 */
const val REACHED_ENTITY_INTERACTION = "cobblemon_dynamics_test:reached_entity_interaction"

/** Holds the GameTests. Wild spawns, weather and night never touch a test. */
@Mod(TEST_MOD_ID)
class DynamicsTestMod(@Suppress("UNUSED_PARAMETER") modBus: IEventBus) {
    init {
        HandlerRegistry.register(TestTransferHandler)
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, PlayerInteractEvent.EntityInteract::class.java) {
            it.target.persistentData.putBoolean(REACHED_ENTITY_INTERACTION, true)
        }
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
