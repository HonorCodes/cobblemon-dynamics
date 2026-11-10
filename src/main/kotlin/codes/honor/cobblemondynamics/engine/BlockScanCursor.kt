package codes.honor.cobblemondynamics.engine

/** Walks a box of block positions, at most [budget] per [step] (CD-ASG-07). X runs fastest, then Z, then Y. */
class BlockScanCursor(
    private val minX: Int,
    private val minY: Int,
    private val minZ: Int,
    maxX: Int,
    maxY: Int,
    maxZ: Int,
    val budget: Int,
) {
    private val sizeX = maxX - minX + 1
    private val sizeY = maxY - minY + 1
    private val sizeZ = maxZ - minZ + 1
    val total: Int = sizeX * sizeY * sizeZ
    private var index = 0

    val done: Boolean get() = index >= total

    fun ticksNeeded(): Int = (total + budget - 1) / budget

    /** Visits the next positions, at most [budget] of them, and returns how many it visited. */
    fun step(visit: (x: Int, y: Int, z: Int) -> Unit): Int {
        val end = minOf(total, index + budget)
        val count = end - index
        while (index < end) {
            val x = index % sizeX
            val z = (index / sizeX) % sizeZ
            val y = index / (sizeX * sizeZ)
            visit(minX + x, minY + y, minZ + z)
            index++
        }
        return count
    }
}
