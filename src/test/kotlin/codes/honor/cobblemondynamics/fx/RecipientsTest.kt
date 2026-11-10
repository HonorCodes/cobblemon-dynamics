package codes.honor.cobblemondynamics.fx

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecipientsTest {
    @Test
    fun `CD-FX-08 64_0 blocks receives and 64_1 does not`() {
        assertTrue(Recipients.inRange(64.0 * 64.0, 64))
        assertFalse(Recipients.inRange(64.1 * 64.1, 64))
    }
}
