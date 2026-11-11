package codes.honor.cobblemondynamics.role

import net.minecraft.resources.ResourceLocation
import java.util.concurrent.ConcurrentHashMap

/**
 * The handlers whose hook threw, each with its first cause. The role registry clears them on server stop and on each role file load,
 * so a handler that failed once works again in the next world and after `/reload`.
 */
class DisabledHandlers {
    private val reasons = ConcurrentHashMap<ResourceLocation, String>()

    /** Turns [id] off. True only for the first cause since the last [clear], which is the one to log. */
    fun disable(id: ResourceLocation, reason: String): Boolean = reasons.putIfAbsent(id, reason) == null

    fun isDisabled(id: ResourceLocation): Boolean = reasons.containsKey(id)

    /** Turns every handler on again. True when one was off. */
    fun clear(): Boolean {
        val any = reasons.isNotEmpty()
        reasons.clear()
        return any
    }
}
