package codes.honor.cobblemondynamics.handler.vanilla

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuddingRulesTest {
    /**
     * A cluster of Cobblemon's `#cobblemon:type_gem_clusters` on a face of `cobblemon:deepslate_crystal_core`: in `#c:buds` and
     * `#c:clusters`, with `stage` 0 to 3 and a `facing` away from the core (javap of `TypeGemClusterBlock`, Cobblemon 1.8.4).
     */
    private fun gem(stage: Int) = BudFacts(bud = true, cluster = true, growth = stage, growthMax = 3)

    @Test
    fun `CD-FIX-02 CD-CRY-03 a type gem cluster grows until its last stage, and a large amethyst bud still grows`() {
        assertTrue(BuddingRules.canGrow(gem(0)), "a type gem cluster at stage 0 gives no demand")
        assertTrue(BuddingRules.canGrow(gem(2)), "a type gem cluster at stage 2 gives no demand")
        assertFalse(BuddingRules.canGrow(gem(3)), "a full type gem cluster gives demand")
        // A large amethyst bud is in #c:buds only, and an amethyst cluster in #c:clusters only.
        assertTrue(BuddingRules.canGrow(BudFacts(bud = true)), "a large amethyst bud gives no demand")
        assertFalse(BuddingRules.canGrow(BudFacts(cluster = true)), "an amethyst cluster gives demand")
        // A bud and a cluster with no stage or age is full.
        assertFalse(BuddingRules.canGrow(BudFacts(bud = true, cluster = true)), "a cluster with no stage gives demand")
    }

    @Test
    fun `CD-CRY-03 air or a water source takes a new bud, and a bud that faces another way is not this block's`() {
        assertTrue(BuddingRules.canGrow(BudFacts(open = true)))
        assertFalse(BuddingRules.canGrow(BudFacts(bud = true, facesAway = false)))
        assertFalse(BuddingRules.canGrow(BudFacts()))
    }

    /** Cobblemon 1.8.4's `TumblestoneBlock.canGrow`: a block of `#cobblemon:tumblestone_heat_source` in the 3 by 3 by 3 around it. */
    @Test
    fun `CD-MCH-03 a self-growing tumblestone has demand only beside a heat source and with a next stage`() {
        val beside = CrystalFacts(selfGrowing = true, heatSourceNear = true, hasNextStage = true)
        assertTrue(BuddingRules.hasDemand(beside) { emptyList() }, "a small budding tumblestone beside lava")
        assertFalse(BuddingRules.hasDemand(beside.copy(heatSourceNear = false)) { emptyList() }, "one with no heat source")
        assertFalse(BuddingRules.hasDemand(beside.copy(hasNextStage = false)) { emptyList() }, "one at its last stage")
        // Its own faces never matter: a tumblestone grows by itself, in place.
        assertFalse(BuddingRules.hasDemand(beside.copy(heatSourceNear = false)) { listOf(BudFacts(open = true)) })
    }

    @Test
    fun `CD-MCH-04 a citrine bud on a face gives demand, and the extra tick of the budding citrine is its scheduled tick`() {
        val citrine = CrystalFacts(scheduledTick = true)
        val bud = BudFacts(bud = BuddingRules.isBud(inBudsTag = false, inCrystalBuds = true))
        assertTrue(BuddingRules.hasDemand(citrine) { listOf(bud) }, "a citrine bud of crystal/buds")
        assertFalse(BuddingRules.hasDemand(citrine) { listOf(BudFacts()) }, "a face with a solid block")
        assertEquals(ExtraTick.SCHEDULED, BuddingRules.extraTick(citrine))
        assertEquals(ExtraTick.RANDOM, BuddingRules.extraTick(CrystalFacts()))
        assertEquals(ExtraTick.RANDOM, BuddingRules.extraTick(CrystalFacts(selfGrowing = true, heatSourceNear = true)))
    }

    @Test
    fun `CD-CRY-03 a budding block of the target tag keeps the face rule`() {
        assertTrue(BuddingRules.hasDemand(CrystalFacts()) { listOf(BudFacts(), BudFacts(open = true)) })
        assertFalse(BuddingRules.hasDemand(CrystalFacts()) { listOf(gem(3), BudFacts(cluster = true)) })
        assertTrue(BuddingRules.isBud(inBudsTag = true, inCrystalBuds = false))
        assertFalse(BuddingRules.isBud(inBudsTag = false, inCrystalBuds = false))
    }
}
