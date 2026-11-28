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
    fun `CD-SEC-10 a stranger may not flip a target that another player's worker drives`() {
        assertFalse(FlipRules.mayFlip(stranger, isOperator = false, placer = placer, tetheredOwners = setOf(placer)))
        assertFalse(FlipRules.mayFlip(stranger, isOperator = false, placer = null, hasPasture = false, quickWorkOwner = binder))
    }

    @Test
    fun `CD-WHL-05 current pasture controllers, operators, and Quick Work owners may flip`() {
        assertTrue(FlipRules.mayFlip(binder, false, placer, locked = false, tetheredOwners = setOf(binder)))
        assertTrue(FlipRules.mayFlip(placer, false, placer, locked = false, tetheredOwners = emptySet()))
        assertTrue(FlipRules.mayFlip(stranger, true, placer, locked = true))
        assertTrue(FlipRules.mayFlip(binder, false, placer = null, hasPasture = false, quickWorkOwner = binder))
    }

    @Test
    fun `CD-SEC-10 with no Quick Work owner and no pasture only an operator may flip`() {
        assertFalse(FlipRules.mayFlip(stranger, false, placer = null, hasPasture = false, quickWorkOwner = null))
        assertTrue(FlipRules.mayFlip(stranger, true, placer = null, hasPasture = false, quickWorkOwner = null))
    }

    @Test
    fun `CD-TEAM-01 CD-TEAM-02 teammates and unlocked contributors may flip, while locked pastures and revoked former binders cannot`() {
        val mate = UUID.randomUUID()
        val contributor = UUID.randomUUID()
        val formerBinder = UUID.randomUUID()
        val isMate: (UUID, UUID) -> Boolean = { a, b -> setOf(a, b) == setOf(placer, mate) }
        assertTrue(FlipRules.mayFlip(contributor, false, placer, locked = false, setOf(contributor), areTeammates = isMate))
        assertTrue(FlipRules.mayFlip(contributor, false, null, locked = false, setOf(contributor), areTeammates = isMate))
        assertFalse(FlipRules.mayFlip(contributor, false, placer, locked = true, setOf(contributor), areTeammates = isMate))
        assertFalse(FlipRules.mayFlip(formerBinder, false, placer, locked = false, setOf(placer, contributor), areTeammates = isMate))
        assertTrue(FlipRules.mayFlip(mate, false, placer, locked = true, setOf(contributor), areTeammates = isMate))
        assertTrue(FlipRules.mayFlip(placer, false, placer, locked = true, setOf(contributor), areTeammates = isMate))
    }
}
