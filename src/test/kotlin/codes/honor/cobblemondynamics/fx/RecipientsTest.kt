package codes.honor.cobblemondynamics.fx

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecipientsTest {
    @Test
    fun `CD-FX-08 64_0 blocks receives and 64_1 does not`() {
        assertTrue(Recipients.inRange(64.0 * 64.0, 64))
        assertFalse(Recipients.inRange(64.1 * 64.1, 64))
    }

    @Test
    fun `CD-FX-08 an effect goes only to the players that track the worker, within the radius`() {
        val distance = mapOf("near" to 1.0, "edge" to 64.0, "beyond" to 64.1, "untracked" to 1.0)
        val watchers = listOf("near", "edge", "beyond")
        val recipients = Recipients.of(watchers, 64) { distance.getValue(it).let { d -> d * d } }
        assertEquals(listOf("near", "edge"), recipients)
        assertFalse("untracked" in recipients, "a player who does not track the worker got its effect")
    }
}
