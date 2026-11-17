package codes.honor.cobblemondynamics.handler.tree

import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.MAX_LEAVES
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.MAX_LOGS
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** ADR-0017, decision 4: the blocks that could not stand once the tree is gone join the fell. */
class ShellRuleTest {
    private val base = BlockPos(0, 64, 0)

    /**
     * A jungle tree with cocoa on its trunk, a chain of 3 vines below a leaf, a torch on the trunk and a flower on the grass beside the
     * trunk. Each attached block stands while the block that holds it stands, as `canSurvive` decides it in the level.
     */
    @Test
    fun `CD-LUM-09 cocoa, a chain of 3 vines and a torch join the shell, and a flower on the grass beside the trunk does not`() {
        val grid = TreeGrid()
        val jungle = grid.jungle(base)
        grid.block(base.below(), "minecraft:dirt")
        val holder = HashMap<BlockPos, BlockPos>()
        val cocoa = grid.block(base.above(1).east(), "minecraft:cocoa").also { holder[it] = base.above(1) }
        val leaf = base.offset(2, 4, 0)
        val vines = (1..3).map { grid.block(leaf.below(it), "minecraft:vine") }
        vines.forEachIndexed { i, vine -> holder[vine] = if (i == 0) leaf else vines[i - 1] }
        val torch = grid.block(base.above(2).west(), "minecraft:wall_torch").also { holder[it] = base.above(2) }
        grid.block(base.offset(0, -1, 1), "minecraft:grass_block")
        val flower = grid.block(base.south(), "minecraft:dandelion").also { holder[it] = base.offset(0, -1, 1) }
        val tree = TreeSearch.find(grid.read, base, MAX_LOGS, MAX_LEAVES) ?: error("the jungle tree gives no tree")
        assertEquals(jungle.leaves.toSet(), tree.leaves)
        val neighbours = { pos: BlockPos -> Direction.entries.map { pos.relative(it) }.filterNot { grid.read(it).air } }
        val survives = { pos: BlockPos, gone: Set<BlockPos> -> holder[pos]?.let { it !in tree.cells && it !in gone } ?: true }
        val shell = ShellRule.shell(tree, neighbours, survives = survives) ?: error("the jungle tree gives no shell")
        assertEquals(setOf(cocoa, torch) + vines, shell)
        assertEquals(false, flower in shell, "the flower joined the shell")
    }

    @Test
    fun `CD-LUM-09 a block that stands on its own next to the tree stays out of the shell`() {
        val grid = TreeGrid()
        grid.oak(base)
        val stone = grid.block(base.above().east(), "minecraft:stone")
        val tree = TreeSearch.find(grid.read, base, MAX_LOGS, MAX_LEAVES) ?: error("the oak gives no tree")
        val neighbours = { pos: BlockPos -> Direction.entries.map { pos.relative(it) }.filterNot { grid.read(it).air } }
        assertEquals(emptySet<BlockPos>(), ShellRule.shell(tree, neighbours) { _, _ -> true })
        assertEquals(setOf(stone), ShellRule.shell(tree, neighbours) { pos, _ -> pos != stone })
    }

    /** A block of another mod whose check casts the view to a `Level` throws: the tree then gets no fell, and the role stays on. */
    @Test
    fun `CD-LUM-29 a block whose survival check throws gives no shell`() {
        val grid = TreeGrid()
        grid.oak(base)
        val odd = grid.block(base.above().east(), "examplemod:odd_block")
        val tree = TreeSearch.find(grid.read, base, MAX_LOGS, MAX_LEAVES) ?: error("the oak gives no tree")
        val neighbours = { pos: BlockPos -> Direction.entries.map { pos.relative(it) }.filterNot { grid.read(it).air } }
        val errors = ArrayList<Throwable>()
        val shell = ShellRule.shell(tree, neighbours, { errors += it }) { pos, _ ->
            if (pos == odd) throw ClassCastException("the view is no Level") else true
        }
        assertEquals(null, shell)
        assertEquals(1, errors.size)
    }
}
