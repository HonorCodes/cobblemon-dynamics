package codes.honor.cobblemondynamics.xp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CollectorMenuTest {
    @Test
    fun `CD-XPC-05 the button gives all 1,234 stored XP and leaves 0, and a second press gives nothing`() {
        val store = CollectorStore(1_234) { 100_000 }
        val given = mutableListOf<Int>()
        assertEquals(1_234, CollectorSlots.press(CollectorSlots.COLLECT_BUTTON, store) { given += it })
        assertEquals(listOf(1_234), given)
        assertEquals(0, store.stored)
        assertEquals(0, CollectorSlots.press(CollectorSlots.COLLECT_BUTTON, store) { given += it })
        assertEquals(listOf(1_234), given, "a press of an empty collector gave XP")
    }

    @Test
    fun `CD-XPC-05 another button id gives nothing`() {
        val store = CollectorStore(50) { 100_000 }
        val given = mutableListOf<Int>()
        assertEquals(0, CollectorSlots.press(1, store) { given += it })
        assertTrue(given.isEmpty())
        assertEquals(50, store.stored)
    }

    /**
     * `ClientboundContainerSetDataPacket` writes each value with `writeShort`, and the client reads it back as a signed short, so the
     * test sends each slot through a short (RES13).
     */
    @Test
    fun `CD-XPC-06 100,000, 65,535 and 65,536 XP split into two slots that join back to the same values`() {
        for (xp in listOf(0, 1, 32_767, 32_768, 65_535, 65_536, 100_000, 1_000_000)) {
            val sent = (0 until CollectorSlots.COUNT).map { slot -> CollectorSlots.part(xp, slot).toShort().toInt() }
            assertEquals(xp, CollectorSlots.join(sent[0], sent[1]), "$xp XP joined back as another number")
            for (slot in 0 until CollectorSlots.COUNT) assertTrue(CollectorSlots.part(xp, slot) in 0..0xFFFF, "slot $slot of $xp")
        }
    }
}
