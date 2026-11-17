package codes.honor.cobblemondynamics.compat.railcraft

import com.google.gson.JsonObject
import net.minecraft.core.BlockPos

/**
 * The Stoker's rules on Railcraft Reborn 1.2.17, over plain facts, so that unit tests load them with no Railcraft class.
 *
 * The blast furnace burns 5 of its `burnTime` on every tick, and takes a fuel item only at its process step, every 16 ticks, while
 * `burnTime` is 10 or less. The engine works after the block entities tick, so a worker that leaves 16 after each tick keeps the
 * furnace above that line (CD-FUEL-04, CD-FUEL-13).
 *
 * A steam boiler burns its fuel once per cycle of 16 ticks, or 8 at high pressure. It takes fuel items while its `burnTime` is below
 * the cycle's need, and sets `burnTime` to 0 when it cannot reach it. Each firebox block is its own target, and its worker adds at
 * most the role file's cap to the boiler on each tick, up to the boiler's need (CD-FUEL-05).
 */
object RailcraftFuelRule {
    enum class Kind { BLAST_FURNACE, FIREBOX }

    /** 16 − 5 stays above Railcraft's refuel line of 10 at the next process step, and 2 ticks bring it to 6, below it (CD-FUEL-14). */
    const val FURNACE_MARGIN = 16

    /** CD-FUEL-04: the blast furnace takes a worker from [minLevel] up. A firebox takes a worker of any level. */
    fun admits(kind: Kind, level: Int, minLevel: Int): Boolean = kind == Kind.FIREBOX || level >= minLevel

    /** The furnace's `burnTime` after a worked tick. A furnace that still burns fuel of its own keeps its value. */
    fun furnaceBurn(burnTime: Int): Int = maxOf(burnTime, FURNACE_MARGIN)

    /** One output slot of the furnace's recipe: whether it is empty, whether it holds the item, its count, what the recipe adds, its limit. */
    data class Slot(val empty: Boolean, val sameItem: Boolean, val count: Int, val adding: Int, val limit: Int)

    /** CD-FUEL-04: the recipe fits while its result and its slag each have room. */
    fun fits(slots: List<Slot>): Boolean = slots.all { it.empty || (it.sameItem && it.count + it.adding <= it.limit) }

    /** CD-FUEL-05: the boiler needs fuel while it is formed and holds water. An empty boiler would heat with no steam to make. */
    fun fireboxDemand(formed: Boolean, hasWater: Boolean): Boolean = formed && hasWater

    /** CD-FUEL-05: at most [cap] burn ticks on this tick, and none once the boiler holds what its next cycle burns. */
    fun fireboxGift(burnTime: Float, need: Float, cap: Double): Float = minOf(cap.toFloat(), maxOf(0f, need - burnTime))

    /** CD-CLM-04: every brick of a blast furnace keys its master. Each firebox block keys itself, so it takes its own worker. */
    fun canonical(kind: Kind, pos: BlockPos, master: BlockPos?): BlockPos = if (kind == Kind.FIREBOX) pos else master ?: pos
}

/**
 * `blast_furnace_min_level` (default 50) and `firebox_burn_ticks_per_tick` (default 1.0, a lit vanilla furnace's rate) of
 * `stoker_railcraft.json`. A key with the wrong type takes its default.
 */
data class RailcraftFuelParams(val blastFurnaceMinLevel: Int, val fireboxBurnTicksPerTick: Double) {
    companion object {
        private const val MIN_LEVEL = "blast_furnace_min_level"
        private const val CAP = "firebox_burn_ticks_per_tick"
        private const val DEFAULT_MIN_LEVEL = 50
        private const val DEFAULT_CAP = 1.0

        fun of(params: JsonObject): RailcraftFuelParams = RailcraftFuelParams(
            number(params, MIN_LEVEL)?.asInt ?: DEFAULT_MIN_LEVEL,
            number(params, CAP)?.asDouble?.coerceAtLeast(0.0) ?: DEFAULT_CAP,
        )

        private fun number(params: JsonObject, key: String) = params.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
    }
}
