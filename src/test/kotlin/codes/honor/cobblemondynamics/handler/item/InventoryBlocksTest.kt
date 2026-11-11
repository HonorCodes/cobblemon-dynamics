package codes.honor.cobblemondynamics.handler.item

import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Ruling 5 of the Phase 2 plan: an inventory of several blocks is one source or destination, at one canonical position. */
class InventoryBlocksTest {
    /** Two blocks are joined when one of [inventories] holds both. */
    private fun joinedWithin(vararg inventories: Set<BlockPos>): (BlockPos, BlockPos) -> Boolean =
        { from, to -> inventories.any { from in it && to in it } }

    private fun box(min: BlockPos, max: BlockPos): Set<BlockPos> = BlockPos.betweenClosed(min, max).map(BlockPos::immutable).toSet()

    @Test
    fun `CD-TLK-10 CD-CLM-04 both halves of a double chest give the same canonical position`() {
        val left = BlockPos(4, 64, 3)
        val right = BlockPos(3, 64, 3)
        val joined = joinedWithin(setOf(left, right))
        assertEquals(right, InventoryBlocks.canonical(left, joined = joined))
        assertEquals(right, InventoryBlocks.canonical(right, joined = joined))
    }

    @Test
    fun `CD-CLM-04 every block of a 3 by 3 by 9 item vault gives the same canonical position`() {
        val vault = box(BlockPos(10, 60, -5), BlockPos(12, 62, 3))
        val joined = joinedWithin(vault)
        assertEquals(81, vault.size)
        assertEquals(setOf(BlockPos(10, 60, -5)), vault.map { InventoryBlocks.canonical(it, joined = joined) }.toSet())
    }

    @Test
    fun `CD-TLK-10 a single chest beside another, and each of two vaults that touch, stay targets of their own`() {
        assertEquals(BlockPos(4, 64, 3), InventoryBlocks.canonical(BlockPos(4, 64, 3)) { _, _ -> false })
        val joined = joinedWithin(box(BlockPos(0, 0, 0), BlockPos(1, 1, 1)), box(BlockPos(2, 0, 0), BlockPos(3, 1, 1)))
        assertEquals(BlockPos(0, 0, 0), InventoryBlocks.canonical(BlockPos(1, 1, 1), joined = joined))
        assertEquals(BlockPos(2, 0, 0), InventoryBlocks.canonical(BlockPos(2, 1, 1), joined = joined))
    }

    /** A world where every block joins its neighbours has no end, so only the bound stops the search. */
    @Test
    fun `the search asks about the neighbours of at most MAX_BLOCKS blocks`() {
        var asked = 0
        InventoryBlocks.canonical(BlockPos.ZERO) { _, _ ->
            asked++
            true
        }
        assertTrue(asked <= 6 * InventoryBlocks.MAX_BLOCKS, "the search asked $asked times")
    }
}
