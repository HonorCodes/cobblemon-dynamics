package codes.honor.cobblemondynamics.net

import codes.honor.cobblemondynamics.engine.WorkArea
import codes.honor.cobblemondynamics.id
import io.netty.buffer.ByteBuf
import net.minecraft.core.BlockPos
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.world.phys.AABB

data class AreaWatchPayload(val pos: BlockPos, val watching: Boolean) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<AreaWatchPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<AreaWatchPayload>(id("area_watch"))

        val STREAM_CODEC: StreamCodec<ByteBuf, AreaWatchPayload> = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            AreaWatchPayload::pos,
            ByteBufCodecs.BOOL,
            AreaWatchPayload::watching,
            ::AreaWatchPayload,
        )
    }
}

data class AreaBoundsPayload(
    val pos: BlockPos,
    val active: Boolean,
    val minX: Int,
    val minY: Int,
    val minZ: Int,
    val maxX: Int,
    val maxY: Int,
    val maxZ: Int,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<AreaBoundsPayload> = TYPE

    fun box(): AABB = AABB(minX.toDouble(), minY.toDouble(), minZ.toDouble(), maxX + 1.0, maxY + 1.0, maxZ + 1.0)

    companion object {
        val TYPE = CustomPacketPayload.Type<AreaBoundsPayload>(id("area_bounds"))

        val STREAM_CODEC: StreamCodec<ByteBuf, AreaBoundsPayload> = object : StreamCodec<ByteBuf, AreaBoundsPayload> {
            override fun decode(buffer: ByteBuf): AreaBoundsPayload = AreaBoundsPayload(
                pos = BlockPos.STREAM_CODEC.decode(buffer),
                active = ByteBufCodecs.BOOL.decode(buffer),
                minX = ByteBufCodecs.VAR_INT.decode(buffer),
                minY = ByteBufCodecs.VAR_INT.decode(buffer),
                minZ = ByteBufCodecs.VAR_INT.decode(buffer),
                maxX = ByteBufCodecs.VAR_INT.decode(buffer),
                maxY = ByteBufCodecs.VAR_INT.decode(buffer),
                maxZ = ByteBufCodecs.VAR_INT.decode(buffer),
            )

            override fun encode(buffer: ByteBuf, value: AreaBoundsPayload) {
                BlockPos.STREAM_CODEC.encode(buffer, value.pos)
                ByteBufCodecs.BOOL.encode(buffer, value.active)
                ByteBufCodecs.VAR_INT.encode(buffer, value.minX)
                ByteBufCodecs.VAR_INT.encode(buffer, value.minY)
                ByteBufCodecs.VAR_INT.encode(buffer, value.minZ)
                ByteBufCodecs.VAR_INT.encode(buffer, value.maxX)
                ByteBufCodecs.VAR_INT.encode(buffer, value.maxY)
                ByteBufCodecs.VAR_INT.encode(buffer, value.maxZ)
            }
        }

        fun active(pos: BlockPos, area: WorkArea): AreaBoundsPayload = AreaBoundsPayload(
            pos = pos.immutable(),
            active = true,
            minX = area.minX,
            minY = area.minY,
            minZ = area.minZ,
            maxX = area.maxX,
            maxY = area.maxY,
            maxZ = area.maxZ,
        )

        fun inactive(pos: BlockPos): AreaBoundsPayload = AreaBoundsPayload(pos.immutable(), false, 0, 0, 0, 0, 0, 0)
    }
}
