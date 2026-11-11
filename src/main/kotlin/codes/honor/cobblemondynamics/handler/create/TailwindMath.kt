package codes.honor.cobblemondynamics.handler.create

import com.google.gson.JsonObject
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import kotlin.math.floor

/** The numbers of Tailwind. No Create class, so unit tests load it. */
object TailwindMath {
    private const val EPSILON = 1e-6

    /**
     * floor(S × L × perLevel) virtual sails, with L clamped to 1..100, so that S real and virtual sails stay within [maxTotal]
     * (CD-TLW-03, CD-TLW-04).
     */
    fun bonus(realSails: Int, level: Int, perLevel: Double, maxTotal: Int): Int {
        if (realSails <= 0 || perLevel <= 0.0) return 0
        // The epsilon keeps a product such as 7 × 100 × 0.01 = 6.999… from losing a sail to the floor.
        val raw = floor(realSails.toDouble() * level.coerceIn(1, 100) * perLevel + EPSILON).toLong()
        return raw.coerceAtMost((maxTotal - realSails).toLong()).coerceAtLeast(0).toInt()
    }

    /** Create's windmill speed for [sails] sails: sails ÷ [sailsPerRpm] RPM, at least 1 and at most 16. */
    fun rpm(sails: Int, sailsPerRpm: Int = 8): Int = (sails / sailsPerRpm).coerceIn(1, 16)

    /** A Tailwind spot keeps the worker's body out of the turning sails: beside or behind the bearing's face. */
    fun clearOfSails(bearing: BlockPos, facing: Direction, spot: BlockPos, bodyHeight: Double): Boolean = when (facing) {
        Direction.UP -> spot.y + bodyHeight <= bearing.y + 1 + EPSILON
        Direction.DOWN -> spot.y >= bearing.y
        else -> (spot.x - bearing.x) * facing.stepX + (spot.z - bearing.z) * facing.stepZ <= 0
    }
}

/** The NBT key and rules of [TailwindBonus]. */
object TailwindNbt {
    const val SAILS = "cd_bonus_sails"

    /** The bonus goes only into client packets (CD-TLW-07). A save never holds it, because claims are never saved. */
    fun write(tag: CompoundTag, sails: Int, clientPacket: Boolean) {
        if (clientPacket) tag.putInt(SAILS, sails)
    }

    /** A client packet names the bonus. A save never does, so a load keeps [current]. */
    fun sails(tag: CompoundTag, clientPacket: Boolean, current: Int): Int = if (clientPacket) tag.getInt(SAILS) else current
}

/** `bonus_per_level_of_real_sails` (default 0.01) and `max_total_sails` (default 128), as ARCHITECTURE section 9 gives them. */
data class TailwindParams(val perLevel: Double, val maxTotalSails: Int) {
    companion object {
        private const val PER_LEVEL = "bonus_per_level_of_real_sails"
        private const val MAX_TOTAL = "max_total_sails"
        private val DEFAULT = TailwindParams(0.01, 128)

        /** A key with the wrong type takes its default. */
        fun of(params: JsonObject): TailwindParams = TailwindParams(
            number(params, PER_LEVEL)?.asDouble ?: DEFAULT.perLevel,
            number(params, MAX_TOTAL)?.asInt ?: DEFAULT.maxTotalSails,
        )

        private fun number(params: JsonObject, key: String) = params.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
    }
}
