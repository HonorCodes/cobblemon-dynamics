package codes.honor.cobblemondynamics.handler.tree

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import kotlin.math.floor

/**
 * CD-LUM-30: the blocks that hold up an item frame or a painting. Once one of them is gone, vanilla breaks the entity with a sound and
 * drops it where it hung, so the fell takes it with the tree.
 */
object HangingRule {
    /** The cells behind [box] for an entity that faces [facing]: its box moved half a block toward the wall, as vanilla's support box. */
    fun supports(box: AABB, facing: Direction): List<BlockPos> {
        val behind = facing.opposite
        val support = box.move(behind.stepX * 0.5, behind.stepY * 0.5, behind.stepZ * 0.5).deflate(1.0E-7)
        val cells = ArrayList<BlockPos>()
        for (x in cell(support.minX)..cell(support.maxX)) {
            for (y in cell(support.minY)..cell(support.maxY)) {
                for (z in cell(support.minZ)..cell(support.maxZ)) cells += BlockPos(x, y, z)
            }
        }
        return cells
    }

    private fun cell(coordinate: Double): Int = floor(coordinate).toInt()
}
