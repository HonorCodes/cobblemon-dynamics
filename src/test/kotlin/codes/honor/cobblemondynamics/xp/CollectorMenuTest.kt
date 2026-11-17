package codes.honor.cobblemondynamics.xp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CollectorMenuTest {
    /** A player's total XP, which each grant raises, as `Player.giveExperiencePoints` does when no listener cancels it. */
    private class Recipient(var total: Int = 0) {
        val given = mutableListOf<Int>()

        fun give(xp: Int) {
            given += xp
            total += xp
        }
    }

    private fun press(store: CollectorStore, to: Recipient, button: Int = CollectorSlots.COLLECT_BUTTON, live: Boolean = true): Int =
        CollectorSlots.press(button, store, live, { to.total }, to::give)

    @Test
    fun `CD-XPC-05 the button gives all 1,234 stored XP and leaves 0, and a second press gives nothing`() {
        val store = CollectorStore(1_234) { 100_000 }
        val player = Recipient()
        assertEquals(1_234, press(store, player))
        assertEquals(listOf(1_234), player.given)
        assertEquals(0, store.stored)
        assertEquals(0, press(store, player))
        assertEquals(listOf(1_234), player.given, "a press of an empty collector gave XP")
    }

    /**
     * NeoForge's `giveExperiencePoints` posts `PlayerXpEvent.XpChange`, and a listener that cancels it leaves the player's total XP as it
     * was, after the store gave its XP. The XP goes back, also above a lowered capacity (R86).
     */
    @Test
    fun `CD-XPC-05 a grant that leaves the player's total XP as it was keeps the XP in the store`() {
        val store = CollectorStore(1_234) { 100_000 }
        val offered = mutableListOf<Int>()
        assertEquals(0, CollectorSlots.press(CollectorSlots.COLLECT_BUTTON, store, total = { 50 }) { offered += it })
        assertEquals(listOf(1_234), offered)
        assertEquals(1_234, store.stored, "a canceled grant emptied the store")
        val above = CollectorStore(120_000) { 100_000 }
        assertEquals(0, CollectorSlots.press(CollectorSlots.COLLECT_BUTTON, above, total = { 50 }) {})
        assertEquals(120_000, above.stored, "a canceled grant lost the XP above a lowered capacity")
        val player = Recipient(total = 50)
        assertEquals(1_234, press(store, player))
        assertEquals(1_284, player.total)
        assertEquals(0, store.stored)
    }

    /** A menu that stayed open while its collector was broken and placed again in the same tick gives the old XP to no one. */
    @Test
    fun `CD-XPC-05 a press at a collector that is gone gives nothing`() {
        val store = CollectorStore(1_234) { 100_000 }
        val player = Recipient()
        assertEquals(0, press(store, player, live = false))
        assertTrue(player.given.isEmpty())
        assertEquals(1_234, store.stored)
    }

    @Test
    fun `CD-XPC-05 another button id gives nothing`() {
        val store = CollectorStore(50) { 100_000 }
        val player = Recipient()
        assertEquals(0, press(store, player, button = 1))
        assertTrue(player.given.isEmpty())
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
