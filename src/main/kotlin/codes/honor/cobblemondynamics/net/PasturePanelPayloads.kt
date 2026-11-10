package codes.honor.cobblemondynamics.net

import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.id
import io.netty.buffer.ByteBuf
import net.minecraft.core.UUIDUtil
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import java.util.UUID

/** Client to server: the pasture panel opened. The server finds the pasture through the player's Cobblemon pasture link. */
data object PasturePanelOpenPayload : CustomPacketPayload {
    val TYPE = CustomPacketPayload.Type<PasturePanelOpenPayload>(id("pasture_panel_open"))
    val STREAM_CODEC: StreamCodec<ByteBuf, PasturePanelOpenPayload> = StreamCodec.unit(this)

    override fun type(): CustomPacketPayload.Type<PasturePanelOpenPayload> = TYPE
}

/** Client to server: the pasture panel closed. */
data object PasturePanelClosePayload : CustomPacketPayload {
    val TYPE = CustomPacketPayload.Type<PasturePanelClosePayload>(id("pasture_panel_close"))
    val STREAM_CODEC: StreamCodec<ByteBuf, PasturePanelClosePayload> = StreamCodec.unit(this)

    override fun type(): CustomPacketPayload.Type<PasturePanelClosePayload> = TYPE
}

/** One Pokémon of the pasture list: its Work Stamina as 0 to 100 and its worker state. */
data class PanelEntry(val pokemonId: UUID, val percent: Int, val state: WorkerStatus) {
    companion object {
        val STREAM_CODEC: StreamCodec<ByteBuf, PanelEntry> = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            PanelEntry::pokemonId,
            ByteBufCodecs.VAR_INT,
            PanelEntry::percent,
            StreamCodecs.WORKER_STATUS,
            PanelEntry::state,
            ::PanelEntry,
        )
    }
}

/** Server to client: what one viewer's pasture panel shows. [canToggle] is true for the placer and operators (CD-UI-03). */
data class PasturePanelStatePayload(val workMode: Boolean, val canToggle: Boolean, val entries: List<PanelEntry>) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<PasturePanelStatePayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<PasturePanelStatePayload>(id("pasture_panel_state"))

        val STREAM_CODEC: StreamCodec<ByteBuf, PasturePanelStatePayload> = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            PasturePanelStatePayload::workMode,
            ByteBufCodecs.BOOL,
            PasturePanelStatePayload::canToggle,
            PanelEntry.STREAM_CODEC.apply(ByteBufCodecs.list()),
            PasturePanelStatePayload::entries,
            ::PasturePanelStatePayload,
        )
    }
}
