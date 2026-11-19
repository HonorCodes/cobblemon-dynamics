package codes.honor.cobblemondynamics.handler.tree

import codes.honor.cobblemondynamics.engine.SwitchProbes
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
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

    /** R94: the switch asks the Lumberjack's demand at each poll of a worker in Standby, through the slot's memo for that role. */
    @Test
    fun `CD-LSH-34 a kept memo that waits on a refused tree searches once in 1,200 ticks, and a new memo each poll does not`() {
        val probes = SwitchProbes()
        val lumberjack = ResourceLocation.parse("cobblemon_dynamics:lumberjack")
        probes.follow(BlockPos.ZERO to lumberjack)
        var searches = 0
        for (now in 0L until 1_200L step 20L) {
            probes.follow(BlockPos.ZERO to lumberjack)
            val memo = probes.memo(lumberjack)
            val wait = memo.value as? TreeWait ?: TreeWait().also { memo.value = it }
            assertNull(wait.tree(now, baseIsLog = true) { searches++.let { null } })
        }
        assertEquals(1, searches, "a kept memo")
        searches = 0
        for (now in 0L until 1_200L step 20L) assertNull(TreeWait().tree(now, baseIsLog = true) { searches++.let { null } })
        assertEquals(60, searches, "a new memo each poll")
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
