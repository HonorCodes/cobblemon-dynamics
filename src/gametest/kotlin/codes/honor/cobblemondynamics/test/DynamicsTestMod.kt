package codes.honor.cobblemondynamics.test

import net.minecraft.world.item.Items
import net.minecraft.world.item.alchemy.Potions
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent

const val TEST_MOD_ID = "cobblemon_dynamics_test"

/**
 * Set in an entity's NeoForge data by the last listener of an entity click. A click that it marks was not cancelled, so the entity's
 * own interaction ran after it.
 */
const val REACHED_ENTITY_INTERACTION = "cobblemon_dynamics_test:reached_entity_interaction"

/** Holds the GameTests of the full mod set. */
@Mod(TEST_MOD_ID)
class DynamicsTestMod(modBus: IEventBus) {
    init {
        TestBattery.register(modBus)
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, PlayerInteractEvent.EntityInteract::class.java) {
            it.target.persistentData.putBoolean(REACHED_ENTITY_INTERACTION, true)
        }
        // A brew that a stand can repeat: water and a stick give water. No vanilla brew can follow itself.
        NeoForge.EVENT_BUS.addListener<RegisterBrewingRecipesEvent> { it.builder.addMix(Potions.WATER, Items.STICK, Potions.WATER) }
        ProtectedBlocks.register()
        TestWorld.register()
    }
}
