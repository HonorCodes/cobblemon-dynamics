package codes.honor.cobblemondynamics.compat

import codes.honor.cobblemondynamics.OnceLogger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LandClaimGateTest {
    private val lines = mutableListOf<String>()
    private val gate = LandClaimGate(OnceLogger(lines::add))
    private val both = listOf("ftbchunks" to 1, "openpartiesandclaims" to 2)
    private val thrown = listOf(RuntimeException("a"), NoSuchMethodError("shouldPreventInteraction"), NoClassDefFoundError("dev/ftb/X"))

    @Test
    fun `CD-QWK-26 every claim mod must allow, and the first refusal ends the questions`() {
        val asked = mutableListOf<Int>()
        assertTrue(
            gate.allows(ClaimAsker.QUICK_WORK, both) {
                asked += it
                true
            },
        )
        assertEquals(listOf(1, 2), asked)
        asked.clear()
        assertFalse(
            gate.allows(ClaimAsker.QUICK_WORK, both) {
                asked += it
                it != 1
            },
        )
        assertEquals(listOf(1), asked)
        assertTrue(gate.allows(ClaimAsker.QUICK_WORK, emptyList<Pair<String, Int>>()) { error("no claim mod to ask") })
        assertTrue(lines.isEmpty(), "$lines")
    }

    @Test
    fun `CD-QWK-30 a check that throws refuses, and each cause warns once`() {
        for (error in thrown) assertFalse(gate.allows(ClaimAsker.QUICK_WORK, listOf("ftbchunks" to 1)) { throw error }, "$error")
        assertFalse(gate.allows(ClaimAsker.QUICK_WORK, listOf("ftbchunks" to 1)) { throw RuntimeException("the same cause again") })
        assertEquals(3, lines.size, "$lines")
        assertTrue(lines.all { it.startsWith("Quick Work refuses orders while the claim check of ftbchunks fails: ") }, "$lines")
        assertFalse(gate.allows(ClaimAsker.QUICK_WORK, listOf("openpartiesandclaims" to 2)) { throw RuntimeException("a") })
        assertEquals(4, lines.size, "another mod is another cause")
    }

    @Test
    fun `CD-LND-11 a pasture check that throws refuses and warns once per cause`() {
        for (error in thrown) assertFalse(gate.allows(ClaimAsker.PASTURE, listOf("ftbchunks" to 1)) { throw error }, "$error")
        assertFalse(gate.allows(ClaimAsker.PASTURE, listOf("ftbchunks" to 1)) { throw RuntimeException("the same cause again") })
        assertEquals(3, lines.size, "$lines")
        assertTrue(lines.all { it.startsWith("Pastures work no target while the claim check of ftbchunks fails: ") }, "$lines")
        // A check after the one that throws is not asked: the gate refuses at the first refusal.
        assertFalse(gate.allows(ClaimAsker.PASTURE, both) { if (it == 1) throw NoSuchMethodError("getBypassProtection") else true })
        assertEquals(3, lines.size, "the same cause twice logs once: $lines")
        assertFalse(gate.allows(ClaimAsker.PASTURE, listOf("minecolonies" to 3)) { throw RuntimeException("a") })
        assertEquals(4, lines.size, "a second cause logs a second warning: $lines")
    }

    @Test
    fun `CD-LND-11 the warning of a pasture check differs from the warning of Quick Work`() {
        val cause = RuntimeException("the API changed")
        assertFalse(gate.allows(ClaimAsker.QUICK_WORK, listOf("ftbchunks" to 1)) { throw cause })
        assertFalse(gate.allows(ClaimAsker.PASTURE, listOf("ftbchunks" to 1)) { throw cause })
        assertEquals(2, lines.size, "one cause from each asker logs two warnings: $lines")
        assertTrue(lines[0].startsWith("Quick Work refuses orders while the claim check of ftbchunks fails: "), lines[0])
        assertTrue(lines[1].startsWith("Pastures work no target while the claim check of ftbchunks fails: "), lines[1])
    }

    @Test
    fun `CD-HRV-20 a break question that throws refuses and warns once per cause`() {
        for (error in thrown) assertFalse(gate.allows(ClaimAsker.HARVEST, listOf("ftbchunks" to 1)) { throw error }, "$error")
        assertFalse(gate.allows(ClaimAsker.HARVEST, listOf("ftbchunks" to 1)) { throw RuntimeException("the same cause again") })
        assertEquals(3, lines.size, "$lines")
        assertTrue(lines.all { it.startsWith("Harvests break no block while the claim check of ftbchunks fails: ") }, "$lines")
        assertFalse(gate.allows(ClaimAsker.PASTURE, listOf("ftbchunks" to 1)) { throw RuntimeException("a") })
        assertEquals(4, lines.size, "the use question of a pasture warns apart: $lines")
    }
}
