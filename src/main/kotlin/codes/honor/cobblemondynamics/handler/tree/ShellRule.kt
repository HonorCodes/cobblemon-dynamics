package codes.honor.cobblemondynamics.handler.tree

import net.minecraft.core.BlockPos

/**
 * CD-LUM-09: the blocks that could not stand once the tree is gone, such as cocoa, vines, a hanging propagule, a snow layer or a torch.
 * The fell takes them first, with no shape update, so that none of them breaks by vanilla's rule where it hangs, and their loot joins
 * the drops.
 */
object ShellRule {
    /**
     * The blocks next to the tree, or next to other shell blocks, that fail [survives] once the tree is gone. [survives] gets the shell so
     * far, which reads as empty with the tree's cells, so a chain of vines goes as one. [neighbours] gives the cells that a block can
     * hold up. A [survives] that throws, as a block of another mod can, gives no shell and so no fell, and [failed] hears why
     * (CD-LUM-29).
     */
    fun shell(
        tree: Tree,
        neighbours: (BlockPos) -> List<BlockPos>,
        failed: (Throwable) -> Unit = {},
        survives: (BlockPos, Set<BlockPos>) -> Boolean,
    ): Set<BlockPos>? {
        val cells = tree.cells
        val shell = LinkedHashSet<BlockPos>()
        val queue = ArrayDeque<BlockPos>()
        val queued = HashSet<BlockPos>()
        for (cell in cells) for (next in neighbours(cell)) if (next !in cells && queued.add(next)) queue += next
        while (queue.isNotEmpty()) {
            val pos = queue.removeFirst()
            queued -= pos
            if (pos in shell) continue
            val stands = try {
                survives(pos, shell)
            } catch (e: Exception) {
                failed(e)
                return null
            } catch (e: LinkageError) {
                failed(e)
                return null
            }
            if (stands) continue
            shell += pos
            // A block that stood can fall once this one is gone.
            for (next in neighbours(pos)) if (next !in cells && next !in shell && queued.add(next)) queue += next
        }
        return shell
    }
}
