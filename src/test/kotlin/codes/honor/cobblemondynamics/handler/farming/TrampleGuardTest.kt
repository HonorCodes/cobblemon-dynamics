package codes.honor.cobblemondynamics.handler.farming

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrampleGuardTest {
    @Test
    fun `CD-CRP-15 only a directed worker cancels a trample`() {
        assertTrue(TrampleGuard.cancels(cobblemon = true) { true }, "a worker that the engine directs trampled")
        assertFalse(TrampleGuard.cancels(cobblemon = true) { false }, "a wild Cobblemon kept the farmland")
        assertFalse(TrampleGuard.cancels(cobblemon = false) { error("a player's trample asked the engine") }, "a player kept the farmland")
    }
}
