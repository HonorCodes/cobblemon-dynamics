package codes.honor.cobblemondynamics.net

import codes.honor.cobblemondynamics.config.EngineSettings
import codes.honor.cobblemondynamics.engine.IdleReason
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.id
import io.netty.buffer.ByteBuf
import net.minecraft.core.BlockPos
import net.minecraft.core.UUIDUtil
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import java.util.Optional
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

/** One Pokémon of the pasture list: its Work Stamina as 0 to 100, worker state, idle reason and active trait mask. */
data class PanelEntry(
    val pokemonId: UUID,
    val percent: Int,
    val state: WorkerStatus,
    val idleReason: IdleReason = IdleReason.NONE,
    val traitFlags: Int = 0,
) {
    companion object {
        private const val ALL_TRAIT_MASK = 0x1F

        val STREAM_CODEC: StreamCodec<ByteBuf, PanelEntry> = object : StreamCodec<ByteBuf, PanelEntry> {
            override fun decode(buffer: ByteBuf): PanelEntry = PanelEntry(
                pokemonId = UUIDUtil.STREAM_CODEC.decode(buffer),
                percent = ByteBufCodecs.VAR_INT.decode(buffer),
                state = StreamCodecs.WORKER_STATUS.decode(buffer),
                idleReason = StreamCodecs.IDLE_REASON.decode(buffer),
                traitFlags = ByteBufCodecs.VAR_INT.decode(buffer) and ALL_TRAIT_MASK,
            )

            override fun encode(buffer: ByteBuf, value: PanelEntry) {
                UUIDUtil.STREAM_CODEC.encode(buffer, value.pokemonId)
                ByteBufCodecs.VAR_INT.encode(buffer, value.percent)
                StreamCodecs.WORKER_STATUS.encode(buffer, value.state)
                StreamCodecs.IDLE_REASON.encode(buffer, value.idleReason)
                ByteBufCodecs.VAR_INT.encode(buffer, value.traitFlags and ALL_TRAIT_MASK)
            }
        }
    }
}

/** Server to client: what one viewer's pasture panel and Pasture Shelf show (CD-UI-03, CD-TEAM-01 to CD-TEAM-03, CD-SHF-01 to CD-SHF-06). */
data class PasturePanelStatePayload(
    val workMode: Boolean,
    val canToggle: Boolean,
    val entries: List<PanelEntry>,
    val pos: BlockPos? = null,
    val locked: Boolean = false,
    val radius: Int = EngineSettings.DEFAULT.horizontalRadius,
    val maxHorizontalRadius: Int = EngineSettings.DEFAULT.maxHorizontalRadius,
    val canLock: Boolean = canToggle,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<PasturePanelStatePayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<PasturePanelStatePayload>(id("pasture_panel_state"))
        private val ENTRIES_CODEC = PanelEntry.STREAM_CODEC.apply(ByteBufCodecs.list())
        private val OPTIONAL_POS_CODEC = ByteBufCodecs.optional(BlockPos.STREAM_CODEC)

        val STREAM_CODEC: StreamCodec<ByteBuf, PasturePanelStatePayload> = object : StreamCodec<ByteBuf, PasturePanelStatePayload> {
            override fun decode(buffer: ByteBuf): PasturePanelStatePayload = PasturePanelStatePayload(
                workMode = ByteBufCodecs.BOOL.decode(buffer),
                canToggle = ByteBufCodecs.BOOL.decode(buffer),
                entries = ENTRIES_CODEC.decode(buffer),
                pos = OPTIONAL_POS_CODEC.decode(buffer).orElse(null),
                locked = ByteBufCodecs.BOOL.decode(buffer),
                radius = ByteBufCodecs.VAR_INT.decode(buffer),
                maxHorizontalRadius = ByteBufCodecs.VAR_INT.decode(buffer),
                canLock = ByteBufCodecs.BOOL.decode(buffer),
            )

            override fun encode(buffer: ByteBuf, value: PasturePanelStatePayload) {
                ByteBufCodecs.BOOL.encode(buffer, value.workMode)
                ByteBufCodecs.BOOL.encode(buffer, value.canToggle)
                ENTRIES_CODEC.encode(buffer, value.entries)
                OPTIONAL_POS_CODEC.encode(buffer, Optional.ofNullable(value.pos))
                ByteBufCodecs.BOOL.encode(buffer, value.locked)
                ByteBufCodecs.VAR_INT.encode(buffer, value.radius)
                ByteBufCodecs.VAR_INT.encode(buffer, value.maxHorizontalRadius)
                ByteBufCodecs.BOOL.encode(buffer, value.canLock)
            }
        }
    }
}
