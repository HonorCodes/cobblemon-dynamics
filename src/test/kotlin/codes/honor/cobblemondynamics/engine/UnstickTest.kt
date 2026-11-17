package codes.honor.cobblemondynamics.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class UnstickTest {
    /** A Dragonair 0.95 wide pressed against the pasture block's south face, with the corner of its box inside the block's cell. */
    @Test
    fun `a stuck worker narrower than a block goes to the middle of the cell that holds its center`() {
        assertEquals(7.5 to 10.5, Unstick.middle(7.5, 10.1, 0.95))
        assertEquals(8.5 to 9.5, Unstick.middle(8.45, 9.9, 0.97))
        assertEquals(-3.5 to 4.5, Unstick.middle(-3.02, 4.9, 0.6))
    }

    @Test
    fun `a stuck worker in the middle of its cell stays`() {
        assertNull(Unstick.middle(6.5, 6.5, 0.99))
    }

    @Test
    fun `a stuck worker 1 block wide or wider stays, because no cell holds its body`() {
        assertNull(Unstick.middle(7.05, 7.05, 1.0))
        assertNull(Unstick.middle(7.05, 7.05, 1.04))
    }
}
