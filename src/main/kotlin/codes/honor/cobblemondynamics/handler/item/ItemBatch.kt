package codes.honor.cobblemondynamics.handler.item

/** One stack as a batch sees it. [K] is `ItemStack` in the game and a plain key in unit tests, which load no registry. */
internal data class Lot<K : Any>(val item: K, val count: Int)

/** Where a batch takes items from: a container's slots, or the dropped items of a work area. */
internal interface ItemSource<K : Any> {
    val slotCount: Int

    /** What [slot] would give, at most [max] items, with no change. Null when it gives nothing. */
    fun peek(slot: Int, max: Int): Lot<K>?

    /** Takes at most [count] items from [slot] and returns what it took. */
    fun take(slot: Int, count: Int): Lot<K>?

    /** Puts [lot] back where [slot] took it from, and returns how many items did not fit. */
    fun putBack(slot: Int, lot: Lot<K>): Int
}

/** Where a batch puts items: a container. It never keeps an object that it gets (CD-TLK-07). */
internal interface ItemSink<K : Any> {
    /** How many items of [lot] it takes. With [simulate], how many it would take, with no change. */
    fun accept(lot: Lot<K>, simulate: Boolean): Int
}

internal object ItemBatch {
    /**
     * Moves at most [limit] items from [source] to [sink], slot by slot. A slot gives only what the sink would take. Items that the
     * sink then refuses go back to their slot, and what does not fit there goes to [spill], so no item is lost or made (CD-TLK-07,
     * CD-MAG-06). Returns the number of items that reached the sink.
     */
    fun <K : Any> move(source: ItemSource<K>, sink: ItemSink<K>, limit: Int, spill: (Lot<K>) -> Unit): Int {
        var moved = 0
        for (slot in 0 until source.slotCount) {
            if (moved >= limit) break
            val offer = source.peek(slot, limit - moved) ?: continue
            val fits = sink.accept(offer, simulate = true).coerceAtMost(offer.count)
            if (fits <= 0) continue
            val taken = source.take(slot, fits) ?: continue
            val accepted = sink.accept(taken, simulate = false).coerceIn(0, taken.count)
            moved += accepted
            val back = taken.count - accepted
            if (back > 0) {
                val left = source.putBack(slot, Lot(taken.item, back))
                if (left > 0) spill(Lot(taken.item, left))
            }
        }
        return moved
    }

    /** True when some slot of [source] holds an item that [sink] would take (CD-TLK-06, CD-MAG-07). */
    fun <K : Any> canMove(source: ItemSource<K>, sink: ItemSink<K>): Boolean =
        (0 until source.slotCount).any { slot -> source.peek(slot, 1)?.let { sink.accept(it, simulate = true) > 0 } == true }
}
