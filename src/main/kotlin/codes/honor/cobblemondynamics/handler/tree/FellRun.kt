package codes.honor.cobblemondynamics.handler.tree

/**
 * A fell in progress (ADR-0017, decision 8). The claim memo keeps it. Each tick removes the next steps of [plan], at most
 * [blocksPerTick], and the end of the claim removes every step that is left, so a fell that has started completes (CD-LUM-15).
 */
class FellRun(val plan: FellPlan, private val blocksPerTick: Int) {
    private var next = 0

    val done: Boolean get() = next >= plan.steps.size

    /** Removes the steps of this tick through [remove]. True when this call removed the last step. */
    fun step(remove: (FellStep) -> Unit): Boolean = removeUntil(next + blocksPerTick.coerceAtLeast(1), remove)

    /** CD-LUM-15: removes every step that is left. True when this call removed the last step. */
    fun finish(remove: (FellStep) -> Unit): Boolean = removeUntil(plan.steps.size, remove)

    private fun removeUntil(end: Int, remove: (FellStep) -> Unit): Boolean {
        if (done) return false
        val last = minOf(end, plan.steps.size)
        while (next < last) remove(plan.steps[next++])
        return done
    }
}
