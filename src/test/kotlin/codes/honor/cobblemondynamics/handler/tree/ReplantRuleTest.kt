package codes.honor.cobblemondynamics.handler.tree

import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.DARK_OAK_SAPLING
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.OAK_LOG
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.OAK_SAPLING
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.SPRUCE_SAPLING
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.rl
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** ADR-0017, decision 10: the replant takes saplings from the drops, so no sapling comes from nothing. */
class ReplantRuleTest {
    private val base = BlockPos(0, 64, 0)
    private val saplings = setOf(rl(OAK_SAPLING), rl(DARK_OAK_SAPLING), rl(SPRUCE_SAPLING))

    private fun choose(
        drops: List<Pair<ResourceLocation, Int>>,
        twoByTwo: Boolean = false,
        survives: (ResourceLocation, BlockPos) -> Boolean = { _, _ -> true },
    ): ReplantRule.Replant<ResourceLocation>? =
        ReplantRule.choose(drops, base, twoByTwo, saplings::contains, survives) { it == rl(DARK_OAK_SAPLING) }

    /** What drops after the replant. */
    private fun kept(drops: List<Pair<ResourceLocation, Int>>, replant: ReplantRule.Replant<ResourceLocation>?) =
        FellPlan.merged(drops, taken = listOfNotNull(replant?.let { it.item to it.cells.size }))

    @Test
    fun `CD-LUM-17 the drops of an oak with 3 saplings plant one in the base cell and keep 2`() {
        val drops = listOf(rl(OAK_LOG) to 5, rl(OAK_SAPLING) to 3)
        val replant = choose(drops)
        assertEquals(ReplantRule.Replant(rl(OAK_SAPLING), listOf(base)), replant)
        assertEquals(listOf(rl(OAK_LOG) to 5, rl(OAK_SAPLING) to 2), kept(drops, replant))
    }

    @Test
    fun `CD-LUM-17 drops with no sapling plant nothing, and a sapling that cannot survive in the base cell is not planted`() {
        assertNull(choose(listOf(rl(OAK_LOG) to 5)))
        assertNull(choose(listOf(rl(OAK_SAPLING) to 3)) { _, cell -> cell != base })
    }

    @Test
    fun `CD-LUM-18 a dark oak with 5 saplings plants 4 in its 2 x 2`() {
        val drops = listOf(rl(DARK_OAK_SAPLING) to 5)
        val replant = choose(drops, twoByTwo = true)
        assertEquals(ReplantRule.Replant(rl(DARK_OAK_SAPLING), TreeRules.square(base)), replant)
        assertEquals(listOf(rl(DARK_OAK_SAPLING) to 1), kept(drops, replant))
    }

    @Test
    fun `CD-LUM-18 a dark oak with 3 saplings plants none and keeps all`() {
        val drops = listOf(rl(DARK_OAK_SAPLING) to 3)
        val replant = choose(drops, twoByTwo = true)
        assertNull(replant)
        assertEquals(drops, kept(drops, replant))
        assertNull(choose(listOf(rl(DARK_OAK_SAPLING) to 5)), "a dark oak sapling grows alone in a trunk of 1 log")
    }

    @Test
    fun `CD-LUM-18 a mega spruce with 2 saplings plants one at the corner cell`() {
        val replant = choose(listOf(rl(SPRUCE_SAPLING) to 2), twoByTwo = true)
        assertEquals(ReplantRule.Replant(rl(SPRUCE_SAPLING), listOf(base)), replant)
    }

    @Test
    fun `CD-LUM-18 a 2 x 2 whose cell cannot hold a sapling plants one at the corner cell`() {
        val corner = base
        val replant = choose(listOf(rl(SPRUCE_SAPLING) to 4), twoByTwo = true) { _, cell -> cell == corner }
        assertEquals(ReplantRule.Replant(rl(SPRUCE_SAPLING), listOf(corner)), replant)
    }

    /** A claim can change during a fell of several ticks, so the end of the fell asks the use question of each planted cell again. */
    @Test
    fun `CD-LUM-17 a replant whose cell the claim refuses at the end of the fell plants nothing, and every sapling drops`() {
        val drops = listOf(rl(OAK_SAPLING) to 3)
        val replant = choose(drops)
        assertEquals(replant, ReplantRule.allowedBy(replant) { true })
        assertNull(ReplantRule.allowedBy(replant) { false })
        assertEquals(drops, kept(drops, ReplantRule.allowedBy(replant) { false }))
        val square = choose(listOf(rl(DARK_OAK_SAPLING) to 4), twoByTwo = true)
        assertNull(ReplantRule.allowedBy(square) { it != base.east() }, "a 2 x 2 planted with one cell refused")
    }
}
