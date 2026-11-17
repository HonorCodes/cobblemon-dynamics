package codes.honor.cobblemondynamics.handler.tree

/**
 * A fell in progress (ADR-0017, decision 8). The claim memo keeps it. Each tick removes the next steps of [plan], at most
 * [blocksPerTick], and the end of the claim removes every step that is left, so a fell that has started completes (CD-LUM-15). From
 * its start to [release], the run holds a ticket on each chunk of its plan, so that no chunk of it unloads (CD-LUM-27).
 */
class FellRun(val plan: FellPlan, private val blocksPerTick: Int, private val tickets: Tickets = Tickets.NONE) {
    private var next = 0
    private var held = true

    init {
        for (chunk in plan.chunks) tickets.add(chunk)
    }

    val done: Boolean get() = next >= plan.steps.size

    /** Removes the steps of this tick through [remove]. True when this call removed the last step. */
    fun step(remove: (FellStep) -> Unit): Boolean = removeUntil(next + blocksPerTick.coerceAtLeast(1), remove)

    /** CD-LUM-15: removes every step that is left. True when this call removed the last step. */
    fun finish(remove: (FellStep) -> Unit): Boolean = removeUntil(plan.steps.size, remove)

    /** CD-LUM-27: gives back the ticket of each chunk, once, after the drops and the replant. */
    fun release() {
        if (!held) return
        held = false
        for (chunk in plan.chunks) tickets.remove(chunk)
    }

    /** Adds and removes the ticket that keeps one chunk loaded, by the chunk's `ChunkPos.asLong` key. */
    class Tickets(val add: (Long) -> Unit, val remove: (Long) -> Unit) {
        companion object {
            val NONE = Tickets({}, {})
        }
    }

    private fun removeUntil(end: Int, remove: (FellStep) -> Unit): Boolean {
        if (done) return false
        val last = minOf(end, plan.steps.size)
        while (next < last) remove(plan.steps[next++])
        return done
    }
}
