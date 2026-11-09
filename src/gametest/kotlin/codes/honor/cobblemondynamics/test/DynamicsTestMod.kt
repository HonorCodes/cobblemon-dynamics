package codes.honor.cobblemondynamics.test

import com.cobblemon.mod.common.world.gamerules.CobblemonGameRules
import net.minecraft.world.level.GameRules
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.server.ServerStartedEvent

const val TEST_MOD_ID = "cobblemon_dynamics_test"

/** Holds the GameTests. Wild spawns, weather and night never touch a test. */
@Mod(TEST_MOD_ID)
class DynamicsTestMod(@Suppress("UNUSED_PARAMETER") modBus: IEventBus) {
    init {
        NeoForge.EVENT_BUS.addListener<ServerStartedEvent> { event ->
            val server = event.server
            val rules = server.gameRules
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server)
            rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server)
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server)
            rules.getRule(CobblemonGameRules.DO_POKEMON_SPAWNING).set(false, server)
            server.allLevels.forEach { it.dayTime = 6000 }
        }
    }
}
