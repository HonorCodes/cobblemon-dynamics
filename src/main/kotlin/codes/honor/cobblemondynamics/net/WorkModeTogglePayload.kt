package codes.honor.cobblemondynamics.net

import codes.honor.cobblemondynamics.id
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Client to server, from the Work Mode button. It carries the requested state, never a flip, so a repeated payload gives the same
 * result (CD-SEC-05). The server finds the pasture through the player's Cobblemon pasture link.
 */
data class WorkModeTogglePayload(val enabled: Boolean) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<WorkModeTogglePayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<WorkModeTogglePayload>(id("work_mode_toggle"))

        val STREAM_CODEC: StreamCodec<ByteBuf, WorkModeTogglePayload> =
            ByteBufCodecs.BOOL.map(::WorkModeTogglePayload, WorkModeTogglePayload::enabled)
    }
}
