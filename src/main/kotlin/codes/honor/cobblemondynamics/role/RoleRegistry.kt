package codes.honor.cobblemondynamics.role

import codes.honor.cobblemondynamics.LoadLines
import codes.honor.cobblemondynamics.LoadLog
import codes.honor.cobblemondynamics.MixinState
import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.handler.HandlerRegistry
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.vanilla.MuseBonus
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.mojang.logging.LogUtils
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener
import net.minecraft.util.profiling.ProfilerFiller
import org.slf4j.Logger

/** Loads `data/{namespace}/cobblemon_dynamics/roles/{role}.json` on every data pack load and `/reload`. */
object RoleRegistry : SimpleJsonResourceReloadListener(Gson(), "cobblemon_dynamics/roles") {
    private val LOGGER: Logger = LogUtils.getLogger()

    private var parsed: Map<ResourceLocation, RoleDefinition> = emptyMap()
    private val disabledHandlers = DisabledHandlers()

    // Rebuilds after a config change repeat the lines of the last role file load, so each line logs once per load (CD-CFG-10, CD-CMP-10).
    private val log = LoadLog { LOGGER.info(it) }

    // Whether a mixin injected never changes while the game runs, so each mixin warning logs once per run (CD-WHL-10).
    private val mixinWarnings = HashSet<String>()

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

    /** Replaces the parsed role files exactly as a data pack load does, and returns the previous ones. Tests only. */
    @Synchronized
    fun applyForTest(definitions: Map<ResourceLocation, RoleDefinition>): Map<ResourceLocation, RoleDefinition> {
        val previous = parsed
        replace(definitions)
        return previous
    }

    /** A role file load, which `/reload` also runs, gives each handler that threw another try. */
    private fun replace(roles: Map<ResourceLocation, RoleDefinition>) {
        parsed = roles
        log.newLoad()
        disabledHandlers.clear()
        rebuild()
    }

    /** On server stop: a handler that threw in this world works again in the next one, also in the same JVM. */
    @Synchronized
    fun reset() {
        if (disabledHandlers.clear()) rebuild()
    }

    /** Applies the live `roles.disabled` list, the handler set and the mixin flags to the last parsed role files. */
    @Synchronized
    fun rebuild() {
        val roles = RoleSet(parsed, ::handlerFor, WorkEngineSettings.current.disabledRoles, log::info)
        val required = MixinGate.required(MuseBonus.withApothicEnchanting)
        current = MixinGate.apply(roles, MixinState::applied, required) { if (mixinWarnings.add(it)) LOGGER.warn(it) }
        log.info(LoadLines.roles(parsed.size, current.active.size, current.disabled))
    }

    /**
     * Turns off every role of [handlerId], for a handler whose hook failed, until the server stops or the role files load again. Logs one
     * warning per handler in that time.
     */
    fun disableHandler(handlerId: ResourceLocation, reason: String) {
        if (!disabledHandlers.disable(handlerId, reason)) return
        LOGGER.warn("Handler {} is disabled: {}", handlerId, reason)
        rebuild()
    }

    /**
     * Whether the role [id] is a logistics role, whose handler shares its targets (CD-CLM-10). The role files of the last load decide,
     * also while the config or a failed handler turns the role off, so its bindings never start to reserve their containers.
     */
    fun sharesTargets(id: ResourceLocation): Boolean = parsed[id]?.let { HandlerRegistry.get(it.handler) }?.sharesTargets == true

    /** The claim lane of the role [id]'s handler (CD-CRP-06), from the role files of the last load, as [sharesTargets] reads them. */
    fun laneOf(id: ResourceLocation): String? = parsed[id]?.let { HandlerRegistry.get(it.handler) }?.claimLane

    private fun handlerFor(id: ResourceLocation): TargetHandler? = if (disabledHandlers.isDisabled(id)) null else HandlerRegistry.get(id)
}
