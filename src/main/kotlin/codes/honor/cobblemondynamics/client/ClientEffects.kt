package codes.honor.cobblemondynamics.client

import codes.honor.cobblemondynamics.fx.WorkFocusEntity
import codes.honor.cobblemondynamics.registry.DynamicsEntities
import net.minecraft.client.renderer.entity.NoopRenderer
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.EntityRenderersEvent

/** The client side of the work effects, which play no sound (CD-FX-09). Only client code paths may touch this object. */
object ClientEffects {
    fun register(modBus: IEventBus) {
        modBus.addListener<EntityRenderersEvent.RegisterRenderers> { event ->
            event.registerEntityRenderer(DynamicsEntities.WORK_FOCUS.get()) { NoopRenderer<WorkFocusEntity>(it) }
        }
    }
}
