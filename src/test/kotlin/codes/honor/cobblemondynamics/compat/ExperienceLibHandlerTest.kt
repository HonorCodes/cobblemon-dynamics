package codes.honor.cobblemondynamics.compat

import codes.honor.cobblemondynamics.compat.experiencelib.CollectorExperienceHandler
import codes.honor.cobblemondynamics.xp.CollectorStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** experiencelib's XP capability on the outputs of a collector (CD-XPC-11), as an Applied Experienced import bus calls it (RES13). */
class ExperienceLibHandlerTest {
    @Test
    fun `CD-XPC-11 over a store of 500 the handler only drains`() {
        val store = CollectorStore(500) { 100_000 }
        var changes = 0
        val handler = CollectorExperienceHandler(store) { changes++ }
        assertEquals(1, handler.getTanks())
        assertEquals(500L, handler.getExperience())
        assertEquals(200L, handler.extractExperience(0, 200, true))
        assertEquals(500, store.stored, "a simulated extract removed XP")
        assertEquals(0, changes)
        assertEquals(200L, handler.extractExperience(0, 200, false))
        assertEquals(300, store.stored)
        assertEquals(1, changes)
        assertEquals(0L, handler.receiveExperience(0, 100, false))
        assertEquals(0L, handler.receiveExperience(0, 100, true))
        assertEquals(0L, handler.receiveExperienceRecipe(0, 100, false))
        handler.setExperience(0, 0)
        handler.setCapacity(0, 0)
        assertEquals(300, store.stored, "setExperience or setCapacity changed the store")
        assertEquals(300L, handler.getExperience())
    }

    @Test
    fun `CD-XPC-11 the handler refuses every insert and gives no more than it holds`() {
        val store = CollectorStore(300) { 100_000 }
        val handler = CollectorExperienceHandler(store) {}
        assertFalse(handler.canAcceptExperience(0, 1))
        assertFalse(handler.canReceive(0))
        assertEquals(0L, handler.getMaxReceive(0))
        assertTrue(handler.canProvideExperience(0, 300))
        assertTrue(handler.canExtract(0))
        assertEquals(300L, handler.extractExperience(0, Long.MAX_VALUE, false))
        assertEquals(0, store.stored)
        assertFalse(handler.canExtract(0))
        assertEquals(0L, handler.extractExperience(0, 10, false))
        assertEquals(0L, handler.extractExperience(1, 10, false), "a second tank gave XP")
        assertEquals(100_000L, handler.getExperienceCapacity())
    }
}
