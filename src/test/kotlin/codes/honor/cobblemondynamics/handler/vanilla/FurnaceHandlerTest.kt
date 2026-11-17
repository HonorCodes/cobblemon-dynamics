package codes.honor.cobblemondynamics.handler.vanilla

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FurnaceHandlerTest {
    /** The Aether's Freezer extends the vanilla furnace through `AbstractAetherFurnaceBlockEntity` (javap of the Aether 1.5.10). */
    @Test
    fun `CD-FIX-01 CD-STK-01 a furnace whose block is in stoker-never does not match, and one outside it does`() {
        assertFalse(FurnaceHandler.isTarget(furnace = true, never = true), "a furnace of the never tag matched")
        assertTrue(FurnaceHandler.isTarget(furnace = true, never = false), "a furnace outside the never tag did not match")
        assertFalse(FurnaceHandler.isTarget(furnace = false, never = false), "a block entity that is no furnace matched")
    }
}
