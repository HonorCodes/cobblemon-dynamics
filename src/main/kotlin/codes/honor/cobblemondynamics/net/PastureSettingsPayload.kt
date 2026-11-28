package codes.honor.cobblemondynamics.net

import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.pasture.WorkModePolicy
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import java.util.Optional

data class PastureSettingsPayload(val radius: Int? = null, val locked: Boolean? = null) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<PastureSettingsPayload> = TYPE

    fun isValid(maxHorizontalRadius: Int): Boolean =
        (radius != null || locked != null) && (radius == null || WorkModePolicy.isValidRadiusStep(radius, maxHorizontalRadius))

    companion object {
        val TYPE = CustomPacketPayload.Type<PastureSettingsPayload>(id("pasture_settings"))

        val STREAM_CODEC: StreamCodec<ByteBuf, PastureSettingsPayload> = StreamCodec.composite(
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT),
            { Optional.ofNullable(it.radius) },
            ByteBufCodecs.optional(ByteBufCodecs.BOOL),
            { Optional.ofNullable(it.locked) },
            { radius, locked -> PastureSettingsPayload(radius.orElse(null), locked.orElse(null)) },
        )
    }
}
