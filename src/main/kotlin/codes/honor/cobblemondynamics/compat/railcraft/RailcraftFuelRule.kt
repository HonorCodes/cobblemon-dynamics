package codes.honor.cobblemondynamics.compat.railcraft

import com.google.gson.JsonObject
import net.minecraft.core.BlockPos

/**
 * The Stoker's rules on the blast furnace of Railcraft Reborn 1.2.17, over plain facts, so that unit tests load them with no Railcraft
 * class. [RailcraftBoilerRule] holds the rules of the solid-fueled boiler.
 *
 * The blast furnace burns 5 of its `burnTime` on every tick, and takes a fuel item only at its process step, every 16 ticks, while
 * `burnTime` is 10 or less. The engine works after the block entities tick, so a worker that leaves 16 after each tick keeps the
 * furnace above that line (CD-FUEL-04, CD-FUEL-13).
 */
object RailcraftFuelRule {
    /** 16 − 5 stays above Railcraft's refuel line of 10 at the next process step, and 2 ticks bring it to 6, below it (CD-FUEL-14). */
    const val FURNACE_MARGIN = 16

    /** CD-FUEL-04: the blast furnace takes a worker from [minLevel] up. */
    fun admits(level: Int, minLevel: Int): Boolean = level >= minLevel

    /** The furnace's `burnTime` after a worked tick. A furnace that still burns fuel of its own keeps its value. */
    fun furnaceBurn(burnTime: Int): Int = maxOf(burnTime, FURNACE_MARGIN)

    /** One output slot of the furnace's recipe: whether it is empty, whether it holds the item, its count, what the recipe adds, its limit. */
    data class Slot(val empty: Boolean, val sameItem: Boolean, val count: Int, val adding: Int, val limit: Int)

    /** CD-FUEL-04: the recipe fits while its result and its slag each have room. */
    fun fits(slots: List<Slot>): Boolean = slots.all { it.empty || (it.sameItem && it.count + it.adding <= it.limit) }

    /** CD-CLM-04: every brick of a blast furnace keys its master. A brick of no formed furnace keys itself. */
    fun canonical(pos: BlockPos, master: BlockPos?): BlockPos = master ?: pos
}

/** `blast_furnace_min_level` (default 50) of `stoker_railcraft.json`. A key with the wrong type takes its default. */
data class RailcraftFuelParams(val blastFurnaceMinLevel: Int) {
    companion object {
        private const val MIN_LEVEL = "blast_furnace_min_level"
        private const val DEFAULT_MIN_LEVEL = 50

        fun of(params: JsonObject): RailcraftFuelParams = RailcraftFuelParams(
            params.get(MIN_LEVEL)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: DEFAULT_MIN_LEVEL,
        )
    }
}
