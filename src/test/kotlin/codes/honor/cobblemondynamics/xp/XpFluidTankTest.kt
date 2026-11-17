package codes.honor.cobblemondynamics.xp

import net.minecraft.core.Direction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The tank of the outputs over fluids named by strings, so it runs with no fluid registry. The fluid handler only wraps it. */
class XpFluidTankTest {
    private fun tank(stored: Int, choice: XpFluid<String>? = XpFluid("xp", 20)) = XpTank(CollectorStore(stored) { 100_000 }) { choice }

    @Test
    fun `CD-XPC-09 at 20 mB per XP, a drain of 30 mB gives 20 mB and removes 1 XP, and a drain of 19 mB gives nothing`() {
        val store = CollectorStore(100) { 100_000 }
        val tank = XpTank(store) { XpFluid("xp", 20) }
        assertEquals(20, tank.drain(30, execute = true))
        assertEquals(99, store.stored)
        assertEquals(0, tank.drain(19, execute = true))
        assertEquals(99, store.stored)
        assertEquals(40, tank.drain(59, execute = false))
        assertEquals(99, store.stored, "a simulated drain removed XP")
        assertEquals(1_980, tank.drain(Int.MAX_VALUE, execute = true))
        assertEquals(0, store.stored)
        assertEquals(0, tank.drain(1_000, execute = true))
    }

    @Test
    fun `CD-XPC-07 CD-XPC-08 the tank holds the stored XP times the ratio of the chosen fluid`() {
        assertEquals(2_000, tank(100).amount())
        assertEquals(2_000_000, tank(100).capacity())
        assertEquals("xp", tank(100).fluid())
        val cei = tank(100, XpFluid("create_enchantment_industry:experience", 1))
        assertEquals(100, cei.amount())
        assertEquals(1, cei.drain(1, execute = true))
        assertEquals(99, cei.amount())
        val none = tank(100, null)
        assertNull(none.fluid())
        assertEquals(0, none.amount())
        assertEquals(0, none.drain(1_000, execute = true))
    }

    @Test
    fun `CD-XPC-10 the tank takes no fluid, the XP fluid or water`() {
        val tank = tank(100)
        assertEquals(0, tank.fill("xp", 1_000))
        assertEquals(0, tank.fill("minecraft:water", 1_000))
        assertFalse(tank.accepts("xp"))
        assertFalse(tank.accepts("minecraft:water"))
        assertEquals(2_000, tank.amount())
    }

    /** The largest capacity at 20 mB per XP stays inside the int of a fluid stack (CD-CFG-20). */
    @Test
    fun `CD-XPC-07 a full collector of 1,000,000 XP holds 20,000,000 mB`() {
        val tank = XpTank(CollectorStore(1_000_000) { 1_000_000 }) { XpFluid("xp", 20) }
        assertEquals(20_000_000, tank.amount())
        assertEquals(20_000_000, tank.capacity())
    }

    @Test
    fun `CD-XPC-07 only the back face, opposite the front, and the bottom face are outputs`() {
        for (front in Direction.Plane.HORIZONTAL) {
            val outputs = Direction.entries.filter { CollectorFaces.isOutput(front, it) }.toSet()
            assertEquals(setOf(front.opposite, Direction.DOWN), outputs, "a collector that faces $front")
            assertFalse(CollectorFaces.isOutput(front, null), "a null side of a collector that faces $front")
        }
        assertTrue(CollectorFaces.isOutput(Direction.SOUTH, Direction.NORTH))
    }
}
