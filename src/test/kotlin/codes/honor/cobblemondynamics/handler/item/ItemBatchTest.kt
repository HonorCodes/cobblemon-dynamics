package codes.honor.cobblemondynamics.handler.item

import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

/** A container of fixed slots with a limit per slot, as a batch sees it. Its items are strings. */
private class Slots(size: Int, private val limit: Int = 64) :
    ItemSource<String>,
    ItemSink<String> {
    val slots = arrayOfNulls<Lot<String>>(size)
    var refuseOnExecute = false
    var refusePutBack = false
    val total: Int get() = slots.sumOf { it?.count ?: 0 }

    fun fill(item: String, count: Int) = apply {
        var left = count
        for (i in slots.indices) if (left > 0) slots[i] = Lot(item, minOf(limit, left)).also { left -= it.count }
    }

    override val slotCount: Int get() = slots.size

    override fun peek(slot: Int, max: Int): Lot<String>? = slots[slot]?.let { Lot(it.item, minOf(max, it.count)) }

    override fun take(slot: Int, count: Int): Lot<String>? {
        val lot = slots[slot] ?: return null
        val n = minOf(count, lot.count)
        slots[slot] = if (n == lot.count) null else lot.copy(count = lot.count - n)
        return Lot(lot.item, n)
    }

    override fun putBack(slot: Int, lot: Lot<String>): Int {
        val current = slots[slot]
        if (refusePutBack || (current != null && current.item != lot.item)) return lot.count
        val n = minOf(limit - (current?.count ?: 0), lot.count)
        slots[slot] = Lot(lot.item, (current?.count ?: 0) + n)
        return lot.count - n
    }

    override fun accept(lot: Lot<String>, simulate: Boolean): Int {
        if (refuseOnExecute && !simulate) return 0
        var left = lot.count
        val order = slots.indices.sortedBy {
            when {
                slots[it]?.item == lot.item -> 0
                slots[it] == null -> 1
                else -> 2
            }
        }
        for (i in order) {
            val current = slots[i]
            if (left == 0 || (current != null && current.item != lot.item)) continue
            val n = minOf(limit - (current?.count ?: 0), left)
            if (!simulate) slots[i] = Lot(lot.item, (current?.count ?: 0) + n)
            left -= n
        }
        return lot.count - left
    }
}

class ItemBatchTest {
    private val params = TransferParams.of(JsonObject())

    @Test
    fun `CD-TLK-03 a batch from a full source moves the rate of the level`() {
        for ((level, expected) in listOf(1 to 1, 25 to 16, 50 to 32, 100 to 64)) {
            val source = Slots(10).fill("dirt", 640)
            val sink = Slots(40)
            assertEquals(expected, ItemBatch.move(source, sink, params.itemsPerBatch(level)) { fail("spilled $it") }, "Lv$level")
            assertEquals(expected, sink.total, "Lv$level sink")
        }
    }

    @Test
    fun `CD-TLK-03 a Lv50 batch never moves a 33rd item, also across slots`() {
        val source = Slots(10).also {
            it.slots[0] = Lot("dirt", 20)
            it.slots[1] = Lot("dirt", 20)
        }
        val sink = Slots(5)
        assertEquals(32, ItemBatch.move(source, sink, params.itemsPerBatch(50)) { fail("spilled $it") })
        assertEquals(8, source.total)
    }

    @Test
    fun `CD-TLK-03 a batch moves fewer only when a container stops it`() {
        assertEquals(10, ItemBatch.move(Slots(3).fill("dirt", 10), Slots(3), 32) { fail("spilled $it") })
        val nearlyFull = Slots(1).also { it.slots[0] = Lot("dirt", 60) }
        assertEquals(4, ItemBatch.move(Slots(3).fill("dirt", 64), nearlyFull, 32) { fail("spilled $it") })
    }

    @Test
    fun `CD-TLK-07 items that the sink refuses after it accepted go back to their slot`() {
        val source = Slots(2).fill("dirt", 100)
        val sink = Slots(4).also { it.refuseOnExecute = true }
        assertEquals(0, ItemBatch.move(source, sink, 64) { fail("spilled $it") })
        assertEquals(100, source.total)
        assertEquals(0, sink.total)
    }

    @Test
    fun `CD-TLK-07 items that fit nowhere spill, and no item is lost or made`() {
        val source = Slots(2).fill("dirt", 100).also { it.refusePutBack = true }
        val sink = Slots(4).also { it.refuseOnExecute = true }
        val spilled = mutableListOf<Lot<String>>()
        ItemBatch.move(source, sink, 64) { spilled += it }
        assertEquals(100, source.total + sink.total + spilled.sumOf { it.count })
    }

    @Test
    fun `CD-MAG-06 a partial insert leaves the rest on the ground`() {
        val ground = Slots(1).fill("cobblestone", 64)
        val container = Slots(1, limit = 10)
        assertEquals(10, ItemBatch.move(ground, container, params.itemsPerBatch(100)) { fail("spilled $it") })
        assertEquals(54, ground.total)
        assertEquals(10, container.total)
    }

    @Test
    fun `CD-MAG-03 with 200 items on the ground, Lv1 inserts 1, Lv50 32 and Lv100 64`() {
        for ((level, expected) in listOf(1 to 1, 50 to 32, 100 to 64)) {
            val ground = Slots(4).fill("cobblestone", 200)
            assertEquals(expected, ItemBatch.move(ground, Slots(27), params.itemsPerBatch(level)) { fail("spilled $it") }, "Lv$level")
        }
    }

    @Test
    fun `CD-TLK-06 CD-MAG-07 there is something to move only while the source holds what the sink takes`() {
        assertFalse(ItemBatch.canMove(Slots(3), Slots(3)))
        val full = Slots(1).fill("stone", 64)
        assertFalse(ItemBatch.canMove(Slots(3).fill("dirt", 5), full))
        assertTrue(ItemBatch.canMove(Slots(3).fill("dirt", 5), Slots(3)))
    }
}
