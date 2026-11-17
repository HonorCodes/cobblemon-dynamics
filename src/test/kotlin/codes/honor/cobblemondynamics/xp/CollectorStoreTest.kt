package codes.honor.cobblemondynamics.xp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CollectorStoreTest {
    @Test
    fun `CD-XPC-03 a store at 99,990 of 100,000 takes 10 and refuses 1 more`() {
        val store = CollectorStore(99_990) { 100_000 }
        assertEquals(10, store.room)
        assertEquals(10, store.insert(25))
        assertEquals(100_000, store.stored)
        assertEquals(0, store.insert(1))
        assertEquals(100_000, store.stored)
        assertEquals(0, store.insert(-5))
    }

    @Test
    fun `CD-XPC-03 a store of 120,000 keeps its XP when the capacity falls to 100,000, and takes none`() {
        var capacity = 1_000_000
        val store = CollectorStore { capacity }
        assertEquals(120_000, store.insert(120_000))
        capacity = 100_000
        assertEquals(120_000, store.stored, "the lowered capacity cut the XP")
        assertEquals(0, store.room)
        assertEquals(0, store.insert(1))
        assertEquals(120_000, store.stored)
        assertEquals(20_000, store.extract(20_000, simulate = false))
        assertEquals(0, store.insert(1), "a store at the capacity took XP")
        assertEquals(1, store.extract(1, simulate = false))
        assertEquals(1, store.insert(5))
    }

    /** A Soul Collector's batch reads the capacity once, so a config reload in the middle of the batch neither loses nor makes XP (R86). */
    @Test
    fun `CD-SOU-07 an insert under a capacity read once ignores a later change of the capacity`() {
        var capacity = 100_000
        val store = CollectorStore(99_000) { capacity }
        val limit = store.capacity()
        assertEquals(1_000, store.roomUnder(limit))
        capacity = 99_500
        assertEquals(500, store.room)
        assertEquals(1_000, store.insert(1_000, limit))
        assertEquals(100_000, store.stored)
        assertEquals(0, store.insert(1, limit))
        assertEquals(0, store.roomUnder(limit))
        assertEquals(0, CollectorStore(5) { 100 }.roomUnder(3), "a store above a limit has room")
    }

    @Test
    fun `CD-XPC-03 an extract gives up to the stored XP, and a simulated one removes nothing`() {
        val store = CollectorStore(500) { 100_000 }
        assertEquals(200, store.extract(200, simulate = true))
        assertEquals(500, store.stored)
        assertEquals(200, store.extract(200, simulate = false))
        assertEquals(300, store.extract(1_000, simulate = false))
        assertEquals(0, store.stored)
        assertEquals(0, store.extract(1, simulate = false))
        assertEquals(0, store.extract(-1, simulate = false))
    }

    /** A load from a save or an item keeps XP above the capacity, as a lowered capacity does, and never goes below 0. */
    @Test
    fun `CD-XPC-03 CD-XPC-13 a load keeps every point of XP`() {
        val store = CollectorStore { 100_000 }
        store.load(120_000)
        assertEquals(120_000, store.stored)
        store.load(-3)
        assertEquals(0, store.stored)
        assertEquals(0, CollectorStore(-7) { 100_000 }.stored)
    }

    @Test
    fun `CD-XPC-13 the tooltip's counts have a comma between thousands`() {
        assertEquals("12,345", CollectorStore.format(12_345))
        assertEquals("100,000", CollectorStore.format(100_000))
        assertEquals("0", CollectorStore.format(0))
    }
}
