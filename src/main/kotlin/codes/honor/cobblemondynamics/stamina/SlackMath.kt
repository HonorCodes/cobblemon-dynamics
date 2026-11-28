package codes.honor.cobblemondynamics.stamina

import codes.honor.cobblemondynamics.engine.WorkArea
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation

/** Pure Slacking probability rules across Natures, Dynamics bed tiers, and Friendship (CD-PRG-14, CD-PRG-15). */
object SlackMath {
    private const val DRIVEN_FACTOR = 0.35
    private const val STEADY_FACTOR = 0.75
    private const val DISTRACTED_FACTOR = 1.15
    private const val LAZY_FACTOR = 1.60
    private const val MAX_FRIENDSHIP = 255

    private val DRIVEN = setOf("timid", "hasty", "jolly", "naive", "hardy", "serious")
    private val STEADY = setOf("adamant", "modest", "careful", "calm", "bold", "impish", "docile", "bashful")
    private val DISTRACTED = setOf("lonely", "brave", "naughty", "mild", "quiet", "rash", "gentle", "sassy", "quirky")
    private val LAZY = setOf("relaxed", "lax")

    fun natureFactor(effectiveNature: ResourceLocation?, baseNature: ResourceLocation): Double =
        natureFactor((effectiveNature ?: baseNature).path)

    fun natureFactor(naturePath: String): Double = when (naturePath.lowercase()) {
        in DRIVEN -> DRIVEN_FACTOR
        in STEADY -> STEADY_FACTOR
        in DISTRACTED -> DISTRACTED_FACTOR
        in LAZY -> LAZY_FACTOR
        else -> 1.0
    }

    fun effectiveBedTier(areaMaxTier: Int, lastSleptTier: Int): Int = maxOf(areaMaxTier, lastSleptTier).coerceIn(0, 4)

    fun bedFactor(tier: Int): Double = when (tier.coerceIn(0, 4)) {
        0 -> 1.00
        1 -> 0.85
        2 -> 0.70
        3 -> 0.55
        else -> 0.35
    }

    fun friendshipFactor(friendship: Int): Double = 1.0 - 0.75 * (friendship.coerceIn(0, MAX_FRIENDSHIP) / MAX_FRIENDSHIP.toDouble())

    fun probability(
        baseChance: Double,
        effectiveNature: ResourceLocation?,
        baseNature: ResourceLocation,
        areaMaxBedTier: Int,
        lastSleptBedTier: Int,
        friendship: Int,
    ): Double {
        if (baseChance <= 0.0) return 0.0
        val raw = baseChance *
            natureFactor(effectiveNature, baseNature) *
            bedFactor(effectiveBedTier(areaMaxBedTier, lastSleptBedTier)) *
            friendshipFactor(friendship)
        return raw.coerceIn(0.0, 1.0)
    }

    fun shouldSlack(probability: Double, roll: Double): Boolean = probability > 0.0 && roll < probability

    fun clampWanderTarget(target: BlockPos, area: WorkArea): BlockPos {
        val x = clampAxis(target.x, area.minX, area.maxX)
        val y = target.y.coerceIn(area.minY, area.maxY)
        val z = clampAxis(target.z, area.minZ, area.maxZ)
        return BlockPos(x, y, z)
    }

    private fun clampAxis(value: Int, min: Int, max: Int): Int =
        if (max - min >= 2) value.coerceIn(min + 1, max - 1) else value.coerceIn(min, max)
}
