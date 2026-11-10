package codes.honor.cobblemondynamics.handler.capability

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3

/** The order in which the Generator tries a target's faces (CD-GEN-04, R41). */
object NearestFaces {
    /**
     * The six faces of [block], the face whose center is nearest [from] first. That is the order of each face normal's dot product
     * with the way from the block's center to [from], largest first. Ties keep `Direction`'s order.
     */
    fun of(block: BlockPos, from: Vec3): List<Direction> {
        val dx = from.x - (block.x + 0.5)
        val dy = from.y - (block.y + 0.5)
        val dz = from.z - (block.z + 0.5)
        return Direction.entries.sortedByDescending { dx * it.stepX + dy * it.stepY + dz * it.stepZ }
    }
}
