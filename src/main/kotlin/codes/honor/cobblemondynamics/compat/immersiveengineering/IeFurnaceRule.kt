package codes.honor.cobblemondynamics.compat.immersiveengineering

import com.google.gson.JsonObject
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation

/**
 * The Stoker's rules on the furnaces of Immersive Engineering 12.4.2-194, over plain facts, so that unit tests load them with no IE
 * class. IE's `FurnaceHandler.tickServer` burns the process speed from `burnTime` on each tick, and takes a fuel item only when that
 * leaves `burnTime` at 0 or below. A worker that keeps `burnTime` above the speed therefore fuels the furnace, and its fuel slot
 * stays full (CD-FUEL-01, CD-FUEL-13).
 */
object IeFurnaceRule {
    /** The furnaces of IE that burn fuel, by the path of their multiblock id. The coke oven burns none. */
    enum class Kind(val multiblock: String) {
        ALLOY_KILN("alloy_smelter"),
        BLAST_FURNACE("blast_furnace"),
        IMPROVED_BLAST_FURNACE("advanced_blast_furnace"),
    }

    private const val NAMESPACE = "immersiveengineering"

    /** An improved blast furnace has two preheater places, and each powered preheater adds 1 to its process speed. */
    private const val MAX_PREHEATERS = 2

    /** The burn that a worker leaves when it stops, which IE burns at any process speed within 2 ticks (CD-FUEL-14). */
    private const val STOP_BURN = 2

    fun kindOf(multiblock: ResourceLocation): Kind? =
        if (multiblock.namespace == NAMESPACE) Kind.entries.firstOrNull { it.multiblock == multiblock.path } else null

    /** CD-FUEL-02: the blast furnaces take a worker from [minLevel] up. The kiln takes a worker of any level. */
    fun admits(kind: Kind, level: Int, minLevel: Int): Boolean = kind == Kind.ALLOY_KILN || level >= minLevel

    /** One output slot of a recipe: whether it is empty, whether it holds the result's item, its count, the result's count and its limit. */
    data class Slot(val empty: Boolean, val sameItem: Boolean, val count: Int, val adding: Int, val limit: Int)

    /**
     * A copy of IE's private `FurnaceHandler.getRecipe`: each output slot is empty, or holds the result's item (by item, not by
     * components) with room for the result within the inventory's slot limit.
     */
    fun fits(slots: List<Slot>): Boolean = slots.all { it.empty || (it.sameItem && it.count + it.adding <= it.limit) }

    /**
     * CD-FUEL-01: the highest process speed of the furnace + 1, so that IE's tick never ends at 0. Each preheater beside an improved
     * furnace can add 1 to its speed in any tick, so the value counts every preheater that is there, powered or not.
     */
    fun burnValue(kind: Kind, preheaters: Int): Int =
        1 + (if (kind == Kind.IMPROVED_BLAST_FURNACE) preheaters.coerceIn(0, MAX_PREHEATERS) else 0) + 1

    /** CD-FUEL-14: a stopping worker cuts its own burn, which is at most [written], to 2. A furnace that burns its own fuel keeps it. */
    fun afterStop(burnTime: Int, written: Int): Int = if (burnTime <= written) minOf(burnTime, STOP_BURN) else burnTime

    /** CD-FUEL-03: every block of a multiblock keys its master. A block whose helper names no loaded master keys itself. */
    fun canonical(pos: BlockPos, master: BlockPos?): BlockPos = master ?: pos
}

/** `blast_furnace_min_level` (default 50) of `stoker_immersiveengineering.json`. A key with the wrong type takes its default. */
data class IeFurnaceParams(val blastFurnaceMinLevel: Int) {
    companion object {
        private const val MIN_LEVEL = "blast_furnace_min_level"
        private const val DEFAULT_MIN_LEVEL = 50

        fun of(params: JsonObject): IeFurnaceParams = IeFurnaceParams(
            params.get(MIN_LEVEL)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: DEFAULT_MIN_LEVEL,
        )
    }
}
