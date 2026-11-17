package codes.honor.cobblemondynamics.handler.vanilla

import codes.honor.cobblemondynamics.role.BuiltInRoles
import com.google.gson.JsonObject
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
        assertEquals(ExtraTick.SCHEDULED, BuddingRules.extraTick(citrine, roll = 0))
        assertEquals(ExtraTick.RANDOM, BuddingRules.extraTick(CrystalFacts(), roll = 0))
        assertEquals(ExtraTick.RANDOM, BuddingRules.extraTick(CrystalFacts(selfGrowing = true, heatSourceNear = true), roll = 0))
    }

    /**
     * R88: Unusual End 2.3.1b's `BuddingCitrineUpdateTickProcedure` grows a random face into air on every scheduled tick, with no roll of
     * its own, and the citrine's random tick does nothing. Budding amethyst grows a random face on 1 random tick in 5, so the citrine gets
     * its scheduled tick on 1 extra tick in 5, and its rate matches.
     */
    @Test
    fun `CD-MCH-04 the budding citrine gets its scheduled tick on 1 extra tick in 5, and a random-tick block on every one`() {
        val oneIn = BuddingRules.scheduledTickOneIn(BuiltInRoles.parse("crystal_tender").params)
        assertEquals(5, oneIn, "the default file's scheduled_tick_one_in")
        val ticks = (0 until oneIn).map { BuddingRules.extraTick(CrystalFacts(scheduledTick = true), roll = it) }
        assertEquals(listOf(ExtraTick.SCHEDULED, ExtraTick.NONE, ExtraTick.NONE, ExtraTick.NONE, ExtraTick.NONE), ticks)
        assertTrue((0 until oneIn).all { BuddingRules.extraTick(CrystalFacts(), roll = it) == ExtraTick.RANDOM }, "budding amethyst")
    }

    @Test
    fun `CD-MCH-04 a file with no scheduled tick key gets 1 in 5, and a value below 1 counts as 1`() {
        assertEquals(5, BuddingRules.scheduledTickOneIn(JsonObject()))
        assertEquals(1, BuddingRules.scheduledTickOneIn(JsonObject().apply { addProperty("scheduled_tick_one_in", 0) }))
    }

    /** R88: the citrine's tick grows a bud only into air (`isEmptyBlock`), so a water source on its face is no free face. */
    @Test
    fun `CD-MCH-04 only air is an open face of a block of the scheduled tick tag`() {
        assertFalse(BuddingRules.isOpen(air = false, clusterCanGrow = true, scheduledTick = true), "a water source beside the citrine")
        assertTrue(BuddingRules.isOpen(air = true, clusterCanGrow = true, scheduledTick = true), "air beside the citrine")
        assertTrue(BuddingRules.isOpen(air = false, clusterCanGrow = true, scheduledTick = false), "a water source beside budding amethyst")
        assertFalse(BuddingRules.isOpen(air = false, clusterCanGrow = false, scheduledTick = false), "a solid block")
    }

    /** R88: a claim reads the 27 blocks around a tumblestone at most once per 20 ticks. */
    @Test
    fun `CD-MCH-03 a claim reads a tumblestone's heat source again only after 20 ticks`() {
        assertTrue(BuddingRules.heatCheckDue(checkedAt = null, now = 100), "the claim's first read")
        assertFalse(BuddingRules.heatCheckDue(checkedAt = 100, now = 100))
        assertFalse(BuddingRules.heatCheckDue(checkedAt = 100, now = 119))
        assertTrue(BuddingRules.heatCheckDue(checkedAt = 100, now = 120))
    }

    @Test
    fun `CD-CRY-03 a budding block of the target tag keeps the face rule`() {
        assertTrue(BuddingRules.hasDemand(CrystalFacts()) { listOf(BudFacts(), BudFacts(open = true)) })
        assertFalse(BuddingRules.hasDemand(CrystalFacts()) { listOf(gem(3), BudFacts(cluster = true)) })
        assertTrue(BuddingRules.isBud(inBudsTag = true, inCrystalBuds = false))
        assertFalse(BuddingRules.isBud(inBudsTag = false, inCrystalBuds = false))
    }
}
