package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.handler.HandlerRegistry
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent

const val TEST_MOD_ID = "cobblemon_dynamics_test"

/**
 * Set in an entity's NeoForge data by the last listener of an entity click. A click that it marks was not cancelled, so the entity's
 * own interaction ran after it.
 */
const val REACHED_ENTITY_INTERACTION = "cobblemon_dynamics_test:reached_entity_interaction"

/** Holds the GameTests of the full mod set. */
@Mod(TEST_MOD_ID)
class DynamicsTestMod(@Suppress("UNUSED_PARAMETER") modBus: IEventBus) {
    init {
        HandlerRegistry.register(TestTransferHandler)
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, PlayerInteractEvent.EntityInteract::class.java) {
            it.target.persistentData.putBoolean(REACHED_ENTITY_INTERACTION, true)
        }
        TestWorld.register()
    }
}
