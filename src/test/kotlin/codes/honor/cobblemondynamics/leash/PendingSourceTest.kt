package codes.honor.cobblemondynamics.leash

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PendingSourceTest {
    @Test
    fun `CD-LSH-19 a pending source lives 599 ticks and expires at 600`() {
        val p = PendingSource(BlockPos.ZERO, Direction.UP, clickedAt = 1_000)
        assertFalse(p.expired(1_599, 600))
        assertTrue(p.expired(1_600, 600))
    }
}
