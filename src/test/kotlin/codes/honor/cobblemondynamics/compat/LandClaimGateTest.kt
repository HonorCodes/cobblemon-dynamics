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

    @Test
    fun `CD-QWK-26 every claim mod must allow, and the first refusal ends the questions`() {
        val asked = mutableListOf<Int>()
        assertTrue(
            gate.allows(both) {
                asked += it
                true
            },
        )
        assertEquals(listOf(1, 2), asked)
        asked.clear()
        assertFalse(
            gate.allows(both) {
                asked += it
                it != 1
            },
        )
        assertEquals(listOf(1), asked)
        assertTrue(gate.allows(emptyList<Pair<String, Int>>()) { error("no claim mod to ask") })
        assertTrue(lines.isEmpty(), "$lines")
    }

    @Test
    fun `CD-QWK-30 a check that throws refuses, and each cause warns once`() {
        val thrown = listOf(RuntimeException("a"), NoSuchMethodError("shouldPreventInteraction"), NoClassDefFoundError("dev/ftb/X"))
        for (error in thrown) assertFalse(gate.allows(listOf("ftbchunks" to 1)) { throw error }, "$error")
        assertFalse(gate.allows(listOf("ftbchunks" to 1)) { throw RuntimeException("the same cause again") })
        assertEquals(3, lines.size, "$lines")
        assertTrue(lines.all { it.startsWith("Quick Work refuses orders while the claim check of ftbchunks fails: ") }, "$lines")
        assertFalse(gate.allows(listOf("openpartiesandclaims" to 2)) { throw RuntimeException("a") })
        assertEquals(4, lines.size, "another mod is another cause")
    }
}
