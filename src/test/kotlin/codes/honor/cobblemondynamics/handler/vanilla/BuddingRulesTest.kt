package codes.honor.cobblemondynamics.handler.vanilla

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
}
