package codes.honor.cobblemondynamics.compat.ironfurnaces

/** What the Stoker reads of a furnace of Iron Furnaces. [IronFurnaceHandler] fills it from the block entity. */
data class IronFurnaceFacts(
    /** The furnace runs as a furnace, with neither the Factory nor the Generator augment (`isFurnace()`). */
    val furnaceMode: Boolean,
    /** The furnace's own recipe check passes: an input with a recipe of its recipe type, and room for the result. */
    val canSmelt: Boolean,
    /** Its public `furnaceBurnTime`. */
    val burnTime: Int,
)

/** The pure rules of the Stoker on the furnaces of Iron Furnaces 4.3.2 (CD-FUEL-07, CD-FUEL-13, CD-FUEL-14). */
object IronFurnaceRule {
    /**
     * The furnace's own tick takes 1 burn tick and, at 0, burns a fuel item while its recipe check passes. The engine works after the
     * block entities tick, so 2 burn ticks keep the next tick burning with the fuel slot untouched, and the furnace burns its own fuel
     * again 2 ticks after the work stops.
     */
    const val HEAT_TICKS = 2

    /** CD-FUEL-07: the Factory augment runs on FE, and the Generator's fuel makes FE, which the Generator role covers, so only a furnace. */
    fun hasDemand(f: IronFurnaceFacts): Boolean = f.furnaceMode && f.canSmelt

    /** The burn time after one tick of work, at the furnace's own speed (ruling 3 of R80). Its own fuel keeps its own value. */
    fun burnTime(f: IronFurnaceFacts): Int = if (hasDemand(f)) maxOf(f.burnTime, HEAT_TICKS) else f.burnTime

    /**
     * The output half of the furnace's own protected `canSmelt` (javap of `BlockIronFurnaceTileBase`, 4.3.2): an empty output slot, or
     * the same item with room for the result within the smaller of its stack size and 64.
     */
    fun hasRoom(outputEmpty: Boolean, sameItem: Boolean, outputCount: Int, resultCount: Int, outputMaxStack: Int): Boolean =
        outputEmpty || (sameItem && outputCount + resultCount <= minOf(outputMaxStack, MAX_OUTPUT))

    private const val MAX_OUTPUT = 64
}
