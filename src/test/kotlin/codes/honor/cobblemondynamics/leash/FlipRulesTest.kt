package codes.honor.cobblemondynamics.leash

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class FlipRulesTest {
    private val binder = UUID.randomUUID()
    private val placer = UUID.randomUUID()
    private val stranger = UUID.randomUUID()

    @Test
    fun `CD-SEC-10 a stranger may not flip a target that another player's worker drives`() =
        assertFalse(FlipRules.mayFlip(stranger, isOperator = false, binder = binder, placer = placer))

    @Test
    fun `CD-WHL-05 the binder, the placer and an operator may flip`() {
        assertTrue(FlipRules.mayFlip(binder, false, binder, placer))
        assertTrue(FlipRules.mayFlip(placer, false, binder, placer))
        assertTrue(FlipRules.mayFlip(stranger, true, binder, placer))
    }

    @Test
    fun `CD-SEC-10 with no binder and no placer only an operator may flip`() {
        assertFalse(FlipRules.mayFlip(stranger, false, null, null))
        assertTrue(FlipRules.mayFlip(stranger, true, null, null))
    }
}
