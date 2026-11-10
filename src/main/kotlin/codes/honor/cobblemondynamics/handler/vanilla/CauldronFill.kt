package codes.honor.cobblemondynamics.handler.vanilla

/**
 * A vanilla cauldron as the Pump fills it. NeoForge's cauldron wrapper moves water 1,000 mB at a time, so it cannot fill a cauldron at
 * level 1 or 2. The Pump raises the water level by one step for each 333 mB that it delivers instead, as a dripstone drip raises it
 * by one (CD-PMP-04).
 */
object CauldronFill {
    const val MB_PER_LEVEL = 333

    /** `LayeredCauldronBlock.MAX_FILL_LEVEL`. */
    const val FULL_LEVEL = 3

    enum class Contents { EMPTY, WATER, LAVA, POWDER_SNOW }

    /** A cauldron's water level, 0 for an empty one, and the mB delivered toward its next level. */
    data class Fill(val level: Int, val storedMb: Int)

    /** CD-PMP-06: an empty cauldron, or water below level 3. Lava and powder snow never. */
    fun hasDemand(contents: Contents, level: Int): Boolean =
        contents == Contents.EMPTY || (contents == Contents.WATER && level < FULL_LEVEL)

    /** CD-PMP-04: [offeredMb] on top of [storedMb]. A cauldron that this fills keeps nothing, so the rest of the offer is gone. */
    fun deliver(level: Int, storedMb: Int, offeredMb: Int): Fill {
        if (level >= FULL_LEVEL) return Fill(level, 0)
        val total = storedMb.toLong() + offeredMb.coerceAtLeast(0)
        val steps = minOf(total / MB_PER_LEVEL, (FULL_LEVEL - level).toLong()).toInt()
        val next = level + steps
        return if (next >= FULL_LEVEL) Fill(FULL_LEVEL, 0) else Fill(next, (total - steps.toLong() * MB_PER_LEVEL).toInt())
    }
}
