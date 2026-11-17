package codes.honor.cobblemondynamics.handler.tree

import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.MAX_LEAVES
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.MAX_LOGS
import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** ADR-0017, decision 3: the canopy is the set of natural leaves that vanilla would let decay once the tree is gone. */
class CanopyRuleTest {
    private val base = BlockPos(0, 64, 0)

    @Test
    fun `CD-LUM-05 an oak gives each of its 53 natural leaves`() {
        val grid = TreeGrid()
        val oak = grid.oak(base)
        assertEquals(53, oak.leaves.size)
        assertEquals(oak.leaves.toSet(), CanopyRule.canopy(grid.read, oak.logs.toSet()))
    }

    @Test
    fun `CD-LUM-05 two oaks whose canopies touch each give only their own leaves`() {
        val grid = TreeGrid()
        val west = grid.oak(base)
        val east = grid.oak(base.east(5))
        val westCanopy = CanopyRule.canopy(grid.read, west.logs.toSet()) ?: error("the west oak gives no canopy")
        val eastCanopy = CanopyRule.canopy(grid.read, east.logs.toSet()) ?: error("the east oak gives no canopy")
        assertTrue(westCanopy.none { it in east.leaves }, "the west oak takes a leaf of the east oak")
        assertTrue(eastCanopy.none { it in west.leaves }, "the east oak takes a leaf of the west oak")
        // A leaf within 6 steps of the other trunk stays for that trunk, as vanilla keeps it.
        val shared = base.offset(2, 3, 0)
        assertTrue(shared !in westCanopy && shared !in eastCanopy, "the leaf between the trunks was taken")
        // The far side of each canopy lies 7 or more steps from the other trunk.
        assertTrue(west.leaves.filter { it.x <= -1 }.all { it in westCanopy }, "the west oak misses a leaf of its far side")
        assertTrue(east.leaves.filter { it.x >= 6 }.all { it in eastCanopy }, "the east oak misses a leaf of its far side")
        // Nothing else keeps the leaves of the west oak's top.
        assertTrue(base.above(5) in westCanopy, "the west oak misses its top leaf")
    }

    @Test
    fun `CD-LUM-05 a tree with 1,025 natural leaves gives no tree, and one with 1,024 gives a tree`() {
        for ((extra, found) in listOf(2 to false, 1 to true)) {
            val grid = TreeGrid()
            // 93 logs in a row, under a sheet of leaves 11 wide: each leaf lies 1 to 6 steps from a log, 1,023 in all.
            val logs = (0 until 93).map { grid.log(base.east(it)) }
            for (log in logs) for (dz in -5..5) grid.leaf(log.offset(0, 1, dz))
            listOf(base.offset(-1, 1, 0), base.offset(93, 1, 0)).take(extra).forEach { grid.leaf(it) }
            val canopy = CanopyRule.canopy(grid.read, logs.toSet(), MAX_LEAVES)
            assertEquals(found, canopy != null, "${1_023 + extra} natural leaves")
            assertEquals(found, TreeSearch.find(grid.read, base, MAX_LOGS, MAX_LEAVES) != null, "the search with ${1_023 + extra} leaves")
        }
    }

    @Test
    fun `CD-LUM-06 persistent leaves inside a canopy stay out of it, and natural leaves beyond them stay in it`() {
        val grid = TreeGrid()
        val oak = grid.oak(base)
        val placed = listOf(base.offset(1, 2, 0), base.offset(-1, 2, 0), base.offset(0, 2, 1), base.offset(0, 2, -1))
        for (pos in placed) grid.leaf(pos, persistent = true)
        val canopy = CanopyRule.canopy(grid.read, oak.logs.toSet()) ?: error("the oak gives no canopy")
        assertTrue(placed.none { it in canopy }, "a placed leaf joined the canopy")
        assertEquals(oak.leaves.toSet() - placed.toSet(), canopy)
    }

    @Test
    fun `CD-LUM-05 a log that is no target still keeps the leaves next to it`() {
        val grid = TreeGrid()
        val oak = grid.oak(base)
        // A stripped log in the canopy, which a player could have placed: vanilla keeps every leaf within 6 steps of it.
        val stripped = grid.log(base.offset(2, 3, 0), TreeGrid.STRIPPED_OAK_LOG)
        val canopy = CanopyRule.canopy(grid.read, oak.logs.toSet()) ?: error("the oak gives no canopy")
        assertTrue(stripped.west() !in canopy && stripped.below() !in canopy, "a leaf that the stripped log keeps joined the canopy")
        // 7 steps from the stripped log, around the trunk.
        assertTrue(base.offset(-2, 2, 0) in canopy, "the oak misses a leaf on the far side of the stripped log")
    }

    @Test
    fun `CD-LUM-05 a leaf 7 steps from every log is no leaf of the tree`() {
        val grid = TreeGrid()
        val top = grid.trunk(base, 3).last()
        val chain = (1..7).map { grid.leaf(top.east(it)) }
        val canopy = CanopyRule.canopy(grid.read, setOf(base, base.above(), top)) ?: error("the column gives no canopy")
        assertEquals(chain.take(6).toSet(), canopy)
        assertNull(CanopyRule.canopy(grid.read, setOf(base, base.above(), top), maxLeaves = 5))
    }
}
