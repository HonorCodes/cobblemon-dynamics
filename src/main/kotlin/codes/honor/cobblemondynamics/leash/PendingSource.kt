package codes.honor.cobblemondynamics.leash

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation

/**
 * The source block of a source-and-destination binding, while the Work Leash waits for the destination click (CD-LSH-17). [dimension]
 * is the level of the source click.
 */
data class PendingSource(val dimension: ResourceLocation, val pos: BlockPos, val face: Direction, val clickedAt: Long) {
    /** CD-LSH-19: alive [ttl] - 1 ticks after the click, gone at [ttl]. */
    fun expired(now: Long, ttl: Int): Boolean = now - clickedAt >= ttl

    /**
     * Whether the source counts for a click in the level [dimension]. A source from another level counts as absent: the same
     * coordinates there name a block that the player never clicked, so no protection mod saw a click on it (CD-SEC-11).
     */
    fun liveIn(dimension: ResourceLocation, now: Long, ttl: Int): Boolean = this.dimension == dimension && !expired(now, ttl)

    companion object {
        val CODEC: Codec<PendingSource> = RecordCodecBuilder.create { i ->
            i.group(
                ResourceLocation.CODEC.fieldOf("dimension").forGetter(PendingSource::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(PendingSource::pos),
                Direction.CODEC.fieldOf("face").forGetter(PendingSource::face),
                Codec.LONG.fieldOf("clicked_at").forGetter(PendingSource::clickedAt),
            ).apply(i, ::PendingSource)
        }

        val STREAM_CODEC: StreamCodec<ByteBuf, PendingSource> = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC,
            PendingSource::dimension,
            BlockPos.STREAM_CODEC,
            PendingSource::pos,
            Direction.STREAM_CODEC,
            PendingSource::face,
            ByteBufCodecs.VAR_LONG,
            PendingSource::clickedAt,
            ::PendingSource,
        )
    }
}
