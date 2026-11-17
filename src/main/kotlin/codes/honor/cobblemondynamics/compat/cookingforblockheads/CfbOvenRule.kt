package codes.honor.cobblemondynamics.compat.cookingforblockheads

/** An oven's public `furnaceBurnTime` and `currentItemBurnTime`, whose ratio its flame gauge shows. */
data class OvenBurn(val burnTime: Int, val itemBurnTime: Int)

/** The pure rules of the Stoker on the Oven of Cooking for Blockheads 21.1.24 (CD-FUEL-09, CD-FUEL-13, CD-FUEL-14). */
object CfbOvenRule {
    /**
     * The oven's own tick takes 1 burn tick and, at 0, burns a fuel item while a slot cooks. The engine works after the block entities
     * tick, so 2 burn ticks keep the next tick burning with the fuel slot untouched, and the oven burns its own fuel again 2 ticks after
     * the work stops.
     */
    const val HEAT_TICKS = 2

    /** The oven's container holds its 9 cooking slots at 7 to 15, and `slotCookTime` the cook time of each. */
    const val FIRST_PROCESSING_SLOT = 7
    const val PROCESSING_SLOTS = 9

    /** The cook time of a slot whose food is done and waits for room in the output. */
    const val COOKED = -1

    /** A slot cooks while it holds an item that is not done, which is the oven's own private `shouldConsumeFuel`. */
    fun cooks(filled: List<Boolean>, cookTimes: List<Int>): Boolean =
        filled.indices.any { filled[it] && cookTimes.getOrElse(it) { COOKED } != COOKED }

    /** CD-FUEL-09: the burn after one tick of work, with the gauge full. The oven's own fuel keeps its own values. */
    fun burn(now: OvenBurn, cooks: Boolean): OvenBurn = if (!cooks || now.burnTime >= HEAT_TICKS) now else OvenBurn(HEAT_TICKS, HEAT_TICKS)
}
