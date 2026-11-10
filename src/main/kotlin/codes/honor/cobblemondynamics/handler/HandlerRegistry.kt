package codes.honor.cobblemondynamics.handler

import net.minecraft.resources.ResourceLocation
import java.util.concurrent.ConcurrentHashMap

/** Every registered [TargetHandler] by id. A role whose handler is absent here is disabled. */
object HandlerRegistry {
    private val handlers = ConcurrentHashMap<ResourceLocation, TargetHandler>()

    fun register(handler: TargetHandler) {
        val previous = handlers.putIfAbsent(handler.id, handler)
        check(previous == null) { "Handler ${handler.id} is already registered" }
    }

    fun get(id: ResourceLocation): TargetHandler? = handlers[id]
}
