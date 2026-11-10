package codes.honor.cobblemondynamics.client

import codes.honor.cobblemondynamics.config.ClientConfig
import codes.honor.cobblemondynamics.fx.WorkFocusEntity
import codes.honor.cobblemondynamics.net.WorkSoundPayload
import codes.honor.cobblemondynamics.registry.DynamicsEntities
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.NoopRenderer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.EntityRenderersEvent

/** The client side of the work effects. Only client code paths may touch this object. */
object ClientEffects {
    fun register(modBus: IEventBus) {
        modBus.addListener<EntityRenderersEvent.RegisterRenderers> { event ->
            event.registerEntityRenderer(DynamicsEntities.WORK_FOCUS.get()) { NoopRenderer<WorkFocusEntity>(it) }
        }
    }

    /** Work sounds are off by default, because many looping workers make noise (CD-FX-09). */
    fun playWorkSound(payload: WorkSoundPayload) {
        if (!ClientConfig.workSounds) return
        val level = Minecraft.getInstance().level ?: return
        val entity = level.getEntity(payload.entityId) ?: return
        val sound = SoundEvent.createVariableRangeEvent(payload.sound)
        level.playLocalSound(entity.x, entity.y, entity.z, sound, SoundSource.NEUTRAL, 1f, 1f, false)
    }
}
