package codes.honor.cobblemondynamics.xp

import net.minecraft.core.Direction

/** CD-XPC-07: the faces that pipes drain, the back opposite the front and the bottom. No other face and no null side is an output. */
object CollectorFaces {
    fun isOutput(front: Direction, side: Direction?): Boolean = side == Direction.DOWN || (side != null && side == front.opposite)
}

/**
 * The one tank behind the outputs of a collector (CD-XPC-07 to CD-XPC-10): the stored XP × the ratio of the chosen fluid, in mB. It
 * only drains, and in whole points of XP. It takes the fluid as a value of any type, so the unit tests need no fluid registry. While
 * [live] is false, the collector is gone and its drop carries the XP, so a pipe that kept this tank gets nothing more.
 */
class XpTank<F : Any>(
    private val store: CollectorStore,
    private val live: () -> Boolean = { true },
    private val choice: () -> XpFluid<F>?,
) {
    private fun chosen(): XpFluid<F>? = if (live()) choice() else null

    /** The fluid that the tank holds, or null when no XP fluid is known (CD-XPC-08). */
    fun fluid(): F? = chosen()?.fluid

    /** The fluid in mB. 1,000,000 XP at 20 mB per XP stays inside an int (CD-CFG-20). */
    fun amount(): Int = chosen()?.let { mb(store.stored, it.mbPerXp) } ?: 0

    fun capacity(): Int = chosen()?.let { mb(maxOf(store.capacity(), store.stored), it.mbPerXp) } ?: 0

    /**
     * CD-XPC-09: a drain of up to [maxMb] removes floor([maxMb] ÷ ratio) XP and gives that XP × ratio mB, or only counts it when
     * [execute] is false. Returns the mB given, so no part of a point is lost or made.
     */
    fun drain(maxMb: Int, execute: Boolean): Int {
        val ratio = chosen()?.mbPerXp?.takeIf { it > 0 } ?: return 0
        val xp = store.extract(maxMb.coerceAtLeast(0) / ratio, simulate = !execute)
        return mb(xp, ratio)
    }

    /** CD-XPC-10: the tank takes no fluid. */
    fun fill(fluid: F, mb: Int): Int = 0

    /** CD-XPC-10. */
    fun accepts(fluid: F): Boolean = false

    private fun mb(xp: Int, ratio: Int): Int = (xp.toLong() * ratio).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
