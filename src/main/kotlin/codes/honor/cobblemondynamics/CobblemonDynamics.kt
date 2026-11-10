package codes.honor.cobblemondynamics

import codes.honor.cobblemondynamics.config.ClientConfig
import codes.honor.cobblemondynamics.config.ServerConfig
import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.pasture.WorkModeService
import codes.honor.cobblemondynamics.registry.DynamicsAttachments
import codes.honor.cobblemondynamics.role.RoleRegistry
import com.mojang.logging.LogUtils
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.fml.event.config.ModConfigEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.AddReloadListenerEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import org.slf4j.Logger

@Mod(MOD_ID)
class CobblemonDynamics(modBus: IEventBus, container: ModContainer) {
    init {
        LOGGER.info("Cobblemon Dynamics {} loading", container.modInfo.version)
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC)
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC)
        DynamicsAttachments.REGISTER.register(modBus)
        modBus.addListener<ModConfigEvent.Loading> { onConfigChanged(it.config) }
        modBus.addListener<ModConfigEvent.Reloading> { onConfigChanged(it.config) }
        NeoForge.EVENT_BUS.addListener<AddReloadListenerEvent> { it.addListener(RoleRegistry) }
        NeoForge.EVENT_BUS.addListener<ServerStoppedEvent> { WorkModeService.reset() }
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
