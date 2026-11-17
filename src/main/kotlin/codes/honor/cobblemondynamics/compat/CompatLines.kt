package codes.honor.cobblemondynamics.compat

import codes.honor.cobblemondynamics.LoadLines
import codes.honor.cobblemondynamics.LoadLog
import codes.honor.cobblemondynamics.MixinState
import com.mojang.logging.LogUtils
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.neoforged.fml.ModList
import net.neoforged.neoforge.event.TagsUpdatedEvent
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap

/** CD-OPT-11: what one adapter of an optional mod needs to work: block ids that resolve and mixins that applied. */
data class AdapterNeeds(val blocks: List<ResourceLocation> = emptyList(), val mixins: List<String> = emptyList())

/**
 * CD-OPT-11: when the server's tags load, one INFO line for each loaded mod of [OptionalMods] names each of its adapters as found,
 * or as missing with what is missing: `class` when its compat package did not register it, `block` when a block id does not
 * resolve, and `mixin` when a mixin did not apply. A pack maintainer reads in one line why a target does not work.
 */
object CompatLines {
    private val LOGGER: Logger = LogUtils.getLogger()
    private val log = LoadLog { LOGGER.info(it) }
    private val registered = ConcurrentHashMap<String, AdapterNeeds>()

    /** A compat package registers each of its adapters, behind the `ModList.isLoaded` guard of its mod [modId]. */
    fun register(modId: String, adapter: String, needs: AdapterNeeds = AdapterNeeds()) {
        registered["$modId/$adapter"] = needs
    }

    /** The client's copy of the tags posts this event too, so only a data load of the server logs. */
    fun onTagsUpdated(event: TagsUpdatedEvent) {
        if (event.updateCause != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) return
        val blocks = event.registryAccess.registryOrThrow(Registries.BLOCK)
        log.newLoad()
        for (mod in OptionalMods.ALL) {
            val container = ModList.get().getModContainerById(mod.modId).orElse(null) ?: continue
            val needs = { adapter: String -> registered["${mod.modId}/$adapter"] }
            log.info(line(mod, container.modInfo.version.toString(), needs, blocks::containsKey, MixinState::applied))
        }
    }

    /**
     * The line of [mod] at [version]. [needs] gives what each adapter needs, or null for an adapter that its compat package did not
     * register.
     */
    fun line(
        mod: OptionalMod,
        version: String,
        needs: (String) -> AdapterNeeds?,
        blockExists: (ResourceLocation) -> Boolean,
        applied: (String) -> Boolean,
    ): String {
        val adapters = mod.adapters.map { adapter ->
            val need = needs(adapter)
            val missing = if (need == null) {
                listOf("class")
            } else {
                listOfNotNull(
                    "block".takeIf { need.blocks.any { !blockExists(it) } },
                    "mixin".takeIf { need.mixins.any { !applied(it) } },
                )
            }
            LoadLines.Adapter(adapter, missing)
        }
        return LoadLines.compat(mod.modId, version, adapters)
    }
}
