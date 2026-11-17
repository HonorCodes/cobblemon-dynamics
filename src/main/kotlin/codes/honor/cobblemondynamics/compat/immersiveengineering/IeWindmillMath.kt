package codes.honor.cobblemondynamics.compat.immersiveengineering

import io.netty.buffer.ByteBuf
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec

/**
 * Tailwind's numbers on the IE windmill. `WindmillBlockEntity.getActualTurnSpeed()` multiplies the windmill's own speed by a
 * weather factor w: 0.75 unless it rains, times 0.66 unless it thunders. A worker of level L puts w + (1 − w) × L ÷ 100 in its
 * place, so the sails and the airflow stay IE's, level 100 turns as in a thunderstorm, and no level turns faster (CD-AIR-05,
 * CD-BAL-14). No IE class, so unit tests load it.
 */
object IeWindmillMath {
    private const val DRY = 0.75
    private const val NO_THUNDER = 0.66

    fun weather(raining: Boolean, thundering: Boolean): Double = (if (raining) 1.0 else DRY) * (if (thundering) 1.0 else NO_THUNDER)

    fun factor(weather: Double, level: Int): Double = weather + (1.0 - weather) * level.coerceIn(0, 100) / 100.0

    /** IE's own turn speed [actual] with the worker's factor in place of IE's. A windmill that stands still stays still. */
    fun speed(actual: Double, raining: Boolean, thundering: Boolean, level: Int): Double {
        val weather = weather(raining, thundering)
        return actual * factor(weather, level) / weather
    }

    /** A grant holds for this tick and the next. The handler renews it on every Working tick, so a missed stop lapses within 2 ticks. */
    fun until(now: Long): Long = now + 1

    fun holds(until: Long, now: Long): Boolean = now <= until

    /**
     * The sails turn in the plane of the hub, up to 4 blocks out, and the air in front of the hub must stay free of blocks. A worker
     * stands in front of the hub or behind it, never in the plane of the sails.
     */
    fun clearOfSails(hub: BlockPos, facing: Direction, spot: BlockPos): Boolean =
        (spot.x - hub.x) * facing.stepX + (spot.z - hub.z) * facing.stepZ != 0
}

/** The level of the Tailwind worker of one IE windmill. NeoForge syncs it to the players near the windmill, which animate by it (CD-AIR-06). */
class WindmillBonus(val level: Int) {
    /** The last tick that the bonus holds with no renewal, on the server. A client keeps its copy until the server removes it. */
    var until: Long = Long.MAX_VALUE

    companion object {
        @JvmField
        val STREAM_CODEC: StreamCodec<ByteBuf, WindmillBonus> = ByteBufCodecs.VAR_INT.map({ WindmillBonus(it) }, { it.level })
    }
}
