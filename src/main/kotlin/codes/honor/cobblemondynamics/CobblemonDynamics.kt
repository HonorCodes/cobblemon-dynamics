package codes.honor.cobblemondynamics

import codes.honor.cobblemondynamics.config.ClientConfig
import codes.honor.cobblemondynamics.config.ServerConfig
import codes.honor.cobblemondynamics.config.WorkEngineSettings
import com.mojang.logging.LogUtils
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.fml.event.config.ModConfigEvent
import org.slf4j.Logger

@Mod(MOD_ID)
class CobblemonDynamics(modBus: IEventBus, container: ModContainer) {
    init {
        LOGGER.info("Cobblemon Dynamics {} loading", container.modInfo.version)
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC)
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC)
        modBus.addListener<ModConfigEvent.Loading> { if (it.config.spec === ServerConfig.SPEC) WorkEngineSettings.reload() }
        modBus.addListener<ModConfigEvent.Reloading> { if (it.config.spec === ServerConfig.SPEC) WorkEngineSettings.reload() }
    }

    companion object {
        @JvmField
        val LOGGER: Logger = LogUtils.getLogger()
    }
}
