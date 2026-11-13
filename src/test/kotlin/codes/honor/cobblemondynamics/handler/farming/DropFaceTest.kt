package codes.honor.cobblemondynamics.handler.farming

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DropFaceTest {
    private val crop = BlockPos(10, 64, 10)

    @Test
    fun `CD-HRV-06 a work spot 2 blocks east gives the east face`() {
        assertEquals(Direction.EAST, DropFace.toward(crop, crop.offset(2, 0, 0)))
        assertEquals(Direction.SOUTH, DropFace.toward(crop, crop.offset(1, 0, 2)))
        assertEquals(Direction.WEST, DropFace.toward(crop, crop.offset(-3, 1, 1)))
        assertEquals(Direction.NORTH, DropFace.toward(crop, crop.offset(0, -1, -1)))
    }

    @Test
    fun `CD-HRV-06 a work spot in the crop's column gives the top face`() {
        assertEquals(Direction.UP, DropFace.toward(crop, crop.above()))
        assertEquals(Direction.UP, DropFace.toward(crop, crop.below()))
    }
}
