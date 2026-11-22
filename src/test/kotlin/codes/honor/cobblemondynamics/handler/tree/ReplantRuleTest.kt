package codes.honor.cobblemondynamics.handler.tree

import codes.honor.cobblemondynamics.OnceLogger
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.DARK_OAK_SAPLING
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.OAK_LOG
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.OAK_SAPLING
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.SPRUCE_SAPLING
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.rl
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** ADR-0017, decision 10 and its 1.2.1 amendment: the replant takes its saplings from the drops first and creates the shortfall. */
class ReplantRuleTest {
    private val base = BlockPos(0, 64, 0)
    private val jungleSapling = rl("minecraft:jungle_sapling")

    private fun choose(
        kind: ResourceLocation,
        drops: List<Pair<ResourceLocation, Int>>,
        twoByTwo: Boolean = false,
        survives: (BlockPos) -> Boolean = { true },
        allowed: (BlockPos) -> Boolean = { true },
    ): ReplantRule.Replant<ResourceLocation>? {
        val held = drops.filter { it.first == kind }.sumOf { it.second }
        return ReplantRule.choose(kind, held, base, twoByTwo, { _, cell -> survives(cell) }, { it == rl(DARK_OAK_SAPLING) }, allowed)
    }

    /** What pops after the replant. */
    private fun kept(drops: List<Pair<ResourceLocation, Int>>, replant: ReplantRule.Replant<ResourceLocation>?) =
        FellPlan.merged(drops, taken = listOfNotNull(replant?.let { it.item to it.taken }))

    @Test
    fun `CD-LUM-17 the drops of an oak with 3 saplings plant one in the base cell and keep 2`() {
        val drops = listOf(rl(OAK_LOG) to 5, rl(OAK_SAPLING) to 3)
        val replant = choose(rl(OAK_SAPLING), drops)
        assertEquals(ReplantRule.Replant(rl(OAK_SAPLING), listOf(base), taken = 1), replant)
        assertEquals(listOf(rl(OAK_LOG) to 5, rl(OAK_SAPLING) to 2), kept(drops, replant))
    }

    @Test
    fun `CD-LUM-17 CD-LUM-32 the drops of an oak with no sapling plant one created oak sapling and keep every item`() {
        val drops = listOf(rl(OAK_LOG) to 5)
        val replant = choose(rl(OAK_SAPLING), drops)
        assertEquals(ReplantRule.Replant(rl(OAK_SAPLING), listOf(base), taken = 0), replant)
        assertEquals(1, replant?.created)
        assertEquals(drops, kept(drops, replant))
    }

    @Test
    fun `CD-LUM-17 a sapling that cannot survive in the base cell is not planted`() {
        assertNull(choose(rl(OAK_SAPLING), listOf(rl(OAK_SAPLING) to 3), survives = { it != base }))
    }

    /** A claim can change during a fell of several ticks, so the end of the fell asks the use question of each planted cell again. */
    @Test
    fun `CD-LUM-17 a replant whose cell the claim refuses at the end of the fell plants nothing and keeps all`() {
        val drops = listOf(rl(OAK_LOG) to 5, rl(OAK_SAPLING) to 3)
        val replant = choose(rl(OAK_SAPLING), drops, allowed = { false })
        assertNull(replant)
        assertEquals(drops, kept(drops, replant))
    }

    @Test
    fun `CD-LUM-18 a dark oak with 5 saplings in its drops plants 4 and keeps 1`() {
        val drops = listOf(rl(DARK_OAK_SAPLING) to 5)
        val replant = choose(rl(DARK_OAK_SAPLING), drops, twoByTwo = true)
        assertEquals(ReplantRule.Replant(rl(DARK_OAK_SAPLING), TreeRules.square(base), taken = 4), replant)
        assertEquals(listOf(rl(DARK_OAK_SAPLING) to 1), kept(drops, replant))
    }

    @Test
    fun `CD-LUM-18 a dark oak with 3 plants 4, takes 3, creates 1 and keeps none`() {
        val drops = listOf(rl(DARK_OAK_SAPLING) to 3)
        val replant = choose(rl(DARK_OAK_SAPLING), drops, twoByTwo = true)
        assertEquals(ReplantRule.Replant(rl(DARK_OAK_SAPLING), TreeRules.square(base), taken = 3), replant)
        assertEquals(1, replant?.created)
        assertEquals(emptyList<Pair<ResourceLocation, Int>>(), kept(drops, replant))
    }

    @Test
    fun `CD-LUM-18 a dark oak with none plants 4`() {
        val replant = choose(rl(DARK_OAK_SAPLING), emptyList(), twoByTwo = true)
        assertEquals(ReplantRule.Replant(rl(DARK_OAK_SAPLING), TreeRules.square(base), taken = 0), replant)
        assertEquals(4, replant?.created)
    }

    @Test
    fun `CD-LUM-18 a mega spruce with 2 plants 4`() {
        val replant = choose(rl(SPRUCE_SAPLING), listOf(rl(SPRUCE_SAPLING) to 2), twoByTwo = true)
        assertEquals(ReplantRule.Replant(rl(SPRUCE_SAPLING), TreeRules.square(base), taken = 2), replant)
    }

    @Test
    fun `CD-LUM-18 a mega spruce whose square has one cell over stone plants one at the corner`() {
        val stone = base.east().south()
        val drops = listOf(rl(SPRUCE_SAPLING) to 4)
        val replant = choose(rl(SPRUCE_SAPLING), drops, twoByTwo = true, survives = { it != stone })
        assertEquals(ReplantRule.Replant(rl(SPRUCE_SAPLING), listOf(base), taken = 1), replant)
        assertEquals(listOf(rl(SPRUCE_SAPLING) to 3), kept(drops, replant))
    }

    @Test
    fun `CD-LUM-18 a mega spruce whose square has one refused cell plants one at the corner`() {
        val replant = choose(rl(SPRUCE_SAPLING), listOf(rl(SPRUCE_SAPLING) to 4), twoByTwo = true, allowed = { it != base.east() })
        assertEquals(ReplantRule.Replant(rl(SPRUCE_SAPLING), listOf(base), taken = 1), replant)
    }

    @Test
    fun `CD-LUM-18 a dark oak whose square has a cell over stone plants none and keeps all`() {
        val drops = listOf(rl(DARK_OAK_SAPLING) to 5)
        val replant = choose(rl(DARK_OAK_SAPLING), drops, twoByTwo = true, survives = { it != base.south() })
        assertNull(replant)
        assertEquals(drops, kept(drops, replant))
        assertNull(choose(rl(DARK_OAK_SAPLING), drops), "a dark oak sapling grows alone in a trunk of 1 log")
    }

    @Test
    fun `CD-LUM-32 a 2 x 2 jungle with 2 saplings takes 2, creates 2 and pops no sapling`() {
        val drops = listOf(rl("minecraft:jungle_log") to 24, jungleSapling to 2)
        val replant = choose(jungleSapling, drops, twoByTwo = true)
        assertEquals(2, replant?.taken)
        assertEquals(2, replant?.created)
        assertEquals(listOf(rl("minecraft:jungle_log") to 24), kept(drops, replant))
    }

    @Test
    fun `CD-LUM-32 a 2 x 2 jungle with 6 saplings takes 4, creates none and pops 2`() {
        val drops = listOf(jungleSapling to 6)
        val replant = choose(jungleSapling, drops, twoByTwo = true)
        assertEquals(4, replant?.taken)
        assertEquals(0, replant?.created)
        assertEquals(listOf(jungleSapling to 2), kept(drops, replant))
    }

    /** The review of 1.2.1: the empty stump of a dark oak whose square cannot take four gets one line for each kind. */
    @Test
    fun `CD-LUM-18 a dark oak that plants none logs one line for each kind`() {
        val lines = ArrayList<String>()
        val warnings = OnceLogger(lines::add)
        assertTrue(ReplantRule.warnStump(rl(DARK_OAK_SAPLING), base, warnings))
        assertFalse(ReplantRule.warnStump(rl(DARK_OAK_SAPLING), base.east(9), warnings))
        assertEquals(1, lines.size)
        assertTrue(DARK_OAK_SAPLING in lines.single(), lines.single())
        assertTrue(ReplantRule.warnStump(rl("ns:big_sapling"), base, warnings))
        assertEquals(2, lines.size)
    }

    /**
     * CD-LUM-29: a replant whose planting fails in some cells takes from the drops only what the planted cells used, held items first, so
     * the drops of the failed cells still pop (hardening audit, finding 9).
     */
    @Test
    fun `CD-LUM-29 a failed planting takes no drop`() {
        val square = ReplantRule.Replant("sapling", TreeRules.square(base), taken = 3)
        assertEquals(3, square.takenFor(4))
        assertEquals(3, square.takenFor(3))
        assertEquals(1, square.takenFor(1))
        assertEquals(0, square.takenFor(0))
        assertEquals(0, ReplantRule.Replant("sapling", listOf(base), taken = 0).takenFor(1))
    }
}
