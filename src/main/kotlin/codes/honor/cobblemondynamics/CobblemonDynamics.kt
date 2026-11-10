package codes.honor.cobblemondynamics

import codes.honor.cobblemondynamics.ai.BrainBridge
import codes.honor.cobblemondynamics.ai.DynamicsActivities
import codes.honor.cobblemondynamics.ai.DynamicsMemories
import codes.honor.cobblemondynamics.client.ClientEffects
import codes.honor.cobblemondynamics.config.ClientConfig
import codes.honor.cobblemondynamics.config.ServerConfig
import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.engine.EngineEvents
import codes.honor.cobblemondynamics.handler.HandlerRegistry
import codes.honor.cobblemondynamics.handler.vanilla.FurnaceHandler
import codes.honor.cobblemondynamics.leash.LeashComponents
import codes.honor.cobblemondynamics.leash.LeashInteractions
import codes.honor.cobblemondynamics.leash.LeashResets
import codes.honor.cobblemondynamics.net.DynamicsPayloads
import codes.honor.cobblemondynamics.registry.DynamicsAttachments
import codes.honor.cobblemondynamics.registry.DynamicsEntities
import codes.honor.cobblemondynamics.registry.DynamicsItems
import codes.honor.cobblemondynamics.role.RoleRegistry
import com.mojang.logging.LogUtils
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.fml.event.config.ModConfigEvent
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.AddReloadListenerEvent
import org.slf4j.Logger

@Mod(MOD_ID)
class CobblemonDynamics(modBus: IEventBus, container: ModContainer) {
    init {
        LOGGER.info("Cobblemon Dynamics {} loading", container.modInfo.version)
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC)
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC)
        DynamicsAttachments.REGISTER.register(modBus)
        DynamicsActivities.REGISTER.register(modBus)
        DynamicsMemories.REGISTER.register(modBus)
        DynamicsEntities.REGISTER.register(modBus)
        LeashComponents.REGISTER.register(modBus)
        DynamicsItems.register(modBus)
        DynamicsPayloads.register(modBus)
        if (FMLEnvironment.dist.isClient) ClientEffects.register(modBus)
        // Cobblemon reads TaskConfig.types when it loads the behaviour files, which happens after common setup.
        modBus.addListener<FMLCommonSetupEvent> { it.enqueueWork(Runnable { BrainBridge.registerTaskTypes() }) }
        HandlerRegistry.register(FurnaceHandler)
        modBus.addListener<ModConfigEvent.Loading> { onConfigChanged(it.config) }
        modBus.addListener<ModConfigEvent.Reloading> { onConfigChanged(it.config) }
        NeoForge.EVENT_BUS.addListener<AddReloadListenerEvent> { it.addListener(RoleRegistry) }
        EngineEvents.register()
        LeashInteractions.register()
        LeashResets.register()
    }

    // The server config loads after the data packs, so a load rebuilds the roles too, not only a reload.
    private fun onConfigChanged(config: ModConfig) {
        if (config.spec !== ServerConfig.SPEC) return
        WorkEngineSettings.reload()
        RoleRegistry.rebuild()
    }

    companion object {
        @JvmField
        val LOGGER: Logger = LogUtils.getLogger()
    }
}
