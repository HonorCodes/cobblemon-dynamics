package codes.honor.cobblemondynamics

import com.mojang.logging.LogUtils
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import org.slf4j.Logger

@Mod(MOD_ID)
class CobblemonDynamics(modBus: IEventBus, container: ModContainer) {
    init {
        LOGGER.info("Cobblemon Dynamics {} loading", container.modInfo.version)
    }

    companion object {
        @JvmField
        val LOGGER: Logger = LogUtils.getLogger()
    }
}
