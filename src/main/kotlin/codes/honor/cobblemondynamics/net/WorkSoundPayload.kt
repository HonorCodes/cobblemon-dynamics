package codes.honor.cobblemondynamics.net

import codes.honor.cobblemondynamics.id
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

/** Server to client, on each effect loop of a role with a sound. The client plays it only with `workSounds` on (CD-FX-09). */
data class WorkSoundPayload(val entityId: Int, val sound: ResourceLocation) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<WorkSoundPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<WorkSoundPayload>(id("work_sound"))

        val STREAM_CODEC: StreamCodec<ByteBuf, WorkSoundPayload> = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            WorkSoundPayload::entityId,
            ResourceLocation.STREAM_CODEC,
            WorkSoundPayload::sound,
            ::WorkSoundPayload,
        )
    }
}
