package codes.honor.cobblemondynamics.handler.tree

/**
 * How often a claim searches its tree (CD-LUM-28). A Working worker asks for demand on each tick, so a found tree counts for
 * [RECHECK_TICKS], and a base log with no tree, such as a tree with a bee nest, waits [NO_TREE_WAIT_TICKS] before its next search. A
 * base cell with no log costs no search, and a log there ends the wait.
 */
class TreeWait {
    private var tree: Tree? = null
    private var searchedAt: Long? = null

    /** The tree on the base cell at [now], which [search] finds when the last search is too old. */
    fun tree(now: Long, baseIsLog: Boolean, search: () -> Tree?): Tree? {
        if (!baseIsLog) {
            forget()
            return null
        }
        val at = searchedAt
        if (at != null && now - at < if (tree == null) NO_TREE_WAIT_TICKS else RECHECK_TICKS) return tree
        tree = search()
        searchedAt = now
        return tree
    }

    /** The next call searches, as after a fell. */
    fun forget() {
        tree = null
        searchedAt = null
    }

    companion object {
        const val RECHECK_TICKS = 20L
        const val NO_TREE_WAIT_TICKS = 1_200L
    }
}
