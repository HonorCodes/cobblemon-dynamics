package codes.honor.cobblemondynamics.handler.tree

import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** R82: how often a claim searches its tree, because a Working worker asks for demand on every tick. */
class TreeWaitTest {
    private val tree = Tree(BlockPos.ZERO, setOf(BlockPos.ZERO), emptySet())

    @Test
    fun `CD-LUM-28 a base log with no tree searches once in 1,200 ticks, and a found tree again after 20 ticks`() {
        val refused = TreeWait()
        var searches = 0
        for (now in 0L until 2_400L) assertNull(refused.tree(now, baseIsLog = true) { searches++.let { null } })
        assertEquals(2, searches, "a base whose tree the search refuses")
        val found = TreeWait()
        searches = 0
        for (now in 0L until 100L) assertEquals(tree, found.tree(now, baseIsLog = true) { searches++.let { tree } })
        assertEquals(5, searches, "a found tree")
    }

    @Test
    fun `CD-LUM-28 a base cell with no log costs no search, and a log there ends the wait`() {
        val wait = TreeWait()
        var searches = 0
        assertNull(wait.tree(0, baseIsLog = true) { searches++.let { null } })
        for (now in 1L until 100L) assertNull(wait.tree(now, baseIsLog = false) { searches++.let { tree } })
        assertEquals(1, searches, "a base cell with no log was searched")
        // A sapling that grew into a tree: the search runs at once.
        assertEquals(tree, wait.tree(100, baseIsLog = true) { searches++.let { tree } })
        assertEquals(2, searches)
        wait.forget()
        assertEquals(tree, wait.tree(101, baseIsLog = true) { searches++.let { tree } })
        assertEquals(3, searches, "a forgotten tree was not searched again")
    }
}
