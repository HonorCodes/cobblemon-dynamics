package codes.honor.cobblemondynamics.role

import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.handler.HandlerRegistry
import codes.honor.cobblemondynamics.handler.TargetHandler
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.mojang.logging.LogUtils
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener
import net.minecraft.util.profiling.ProfilerFiller
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap

/** Loads `data/{namespace}/cobblemon_dynamics/roles/{role}.json` on every data pack load and `/reload`. */
object RoleRegistry : SimpleJsonResourceReloadListener(Gson(), "cobblemon_dynamics/roles") {
    private val LOGGER: Logger = LogUtils.getLogger()

    private var parsed: Map<ResourceLocation, RoleDefinition> = emptyMap()
    private val disabledHandlers = ConcurrentHashMap<ResourceLocation, String>()

    // Rebuilds after a config change repeat the lines of the last role file load, so each line logs once per load (CD-CFG-10).
    private val logged = HashSet<String>()

    @Volatile
    var current: RoleSet = RoleSet.EMPTY
        private set

    override fun apply(entries: Map<ResourceLocation, JsonElement>, resourceManager: ResourceManager, profiler: ProfilerFiller) {
        val roles = HashMap<ResourceLocation, RoleDefinition>()
        for ((id, json) in entries) {
            val result = RoleCodec.parse(id, json)
            val role = result.result().orElse(null)
            if (role != null) roles[id] = role else LOGGER.error(RoleCodec.describeError(id, result))
        }
        synchronized(this) { replace(roles) }
    }

    /** Replaces the parsed role files exactly as a data pack load does, and returns the previous ones. GameTests only. */
    @Synchronized
    fun applyForTest(definitions: Map<ResourceLocation, RoleDefinition>): Map<ResourceLocation, RoleDefinition> {
        val previous = parsed
        replace(definitions)
        return previous
    }

    private fun replace(roles: Map<ResourceLocation, RoleDefinition>) {
        parsed = roles
        logged.clear()
        rebuild()
    }

    /** Applies the live `roles.disabled` list and the handler set to the last parsed role files. */
    @Synchronized
    fun rebuild() {
        current = RoleSet(parsed, ::handlerFor, WorkEngineSettings.current.disabledRoles) { if (logged.add(it)) LOGGER.info(it) }
    }

    /** Turns off every role of [handlerId], for a handler whose hook failed. Logs one warning per handler. */
    fun disableHandler(handlerId: ResourceLocation, reason: String) {
        if (disabledHandlers.putIfAbsent(handlerId, reason) != null) return
        LOGGER.warn("Handler {} is disabled: {}", handlerId, reason)
        rebuild()
    }

    private fun handlerFor(id: ResourceLocation): TargetHandler? = if (disabledHandlers.containsKey(id)) null else HandlerRegistry.get(id)
}
