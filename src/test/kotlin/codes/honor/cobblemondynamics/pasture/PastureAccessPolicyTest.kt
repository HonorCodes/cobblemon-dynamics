package codes.honor.cobblemondynamics.pasture

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class PastureAccessPolicyTest {
    private val placer = UUID.randomUUID()
    private val ftbMate = UUID.randomUUID()
    private val opacMate = UUID.randomUUID()
    private val contributor = UUID.randomUUID()
    private val bystander = UUID.randomUUID()
    private val operator = UUID.randomUUID()

    private val teammates: (UUID, UUID) -> Boolean = { a, b ->
        val pair = setOf(a, b)
        pair == setOf(placer, ftbMate) || pair == setOf(placer, opacMate)
    }

    @Test
    fun `CD-TEAM-01 unlocked pasture grants control to placer, operator, teammates and tethered contributors`() {
        val tethered = setOf(contributor)
        assertTrue(PastureAccessPolicy.mayControl(placer, false, placer, locked = false, tethered, teammates))
        assertTrue(PastureAccessPolicy.mayControl(operator, true, placer, locked = false, tethered, teammates))
        assertTrue(PastureAccessPolicy.mayControl(ftbMate, false, placer, locked = false, tethered, teammates))
        assertTrue(PastureAccessPolicy.mayControl(opacMate, false, placer, locked = false, tethered, teammates))
        assertTrue(PastureAccessPolicy.mayControl(contributor, false, placer, locked = false, tethered, teammates))
        assertFalse(PastureAccessPolicy.mayControl(bystander, false, placer, locked = false, tethered, teammates))
    }

    @Test
    fun `CD-TEAM-01 unlocked ownerless pasture grants control to operators and tethered contributors only`() {
        val tethered = setOf(contributor)
        assertTrue(PastureAccessPolicy.mayControl(operator, true, ownerId = null, locked = false, tethered, teammates))
        assertTrue(PastureAccessPolicy.mayControl(contributor, false, ownerId = null, locked = false, tethered, teammates))
        assertFalse(PastureAccessPolicy.mayControl(bystander, false, ownerId = null, locked = false, tethered, teammates))
    }

    @Test
    fun `CD-TEAM-02 locked pasture restricts control to placer, teammates and operators while allowing own-Pokemon sneak-unbind`() {
        val tethered = setOf(contributor)
        assertTrue(PastureAccessPolicy.mayControl(placer, false, placer, locked = true, tethered, teammates))
        assertTrue(PastureAccessPolicy.mayControl(ftbMate, false, placer, locked = true, tethered, teammates))
        assertTrue(PastureAccessPolicy.mayControl(opacMate, false, placer, locked = true, tethered, teammates))
        assertTrue(PastureAccessPolicy.mayControl(operator, true, placer, locked = true, tethered, teammates))
        assertFalse(PastureAccessPolicy.mayControl(contributor, false, placer, locked = true, tethered, teammates))
        assertFalse(PastureAccessPolicy.mayControl(bystander, false, placer, locked = true, tethered, teammates))
        assertFalse(PastureAccessPolicy.mayControl(contributor, false, ownerId = null, locked = true, tethered, teammates))

        assertTrue(PastureAccessPolicy.mayUnbind(contributor, false, contributor, placer, locked = true, tethered, teammates))
        assertFalse(PastureAccessPolicy.mayUnbind(contributor, false, placer, placer, locked = true, tethered, teammates))
        assertTrue(PastureAccessPolicy.mayUnbind(ftbMate, false, contributor, placer, locked = true, tethered, teammates))
        assertTrue(PastureAccessPolicy.mayUnbind(contributor, false, placer, placer, locked = false, tethered, teammates))
        assertFalse(PastureAccessPolicy.mayUnbind(bystander, false, placer, placer, locked = false, tethered, teammates))
    }

    @Test
    fun `CD-TEAM-03 mayLock allows placer, teammates and operators on owned pastures and only operators when ownerId is null`() {
        assertTrue(PastureAccessPolicy.mayLock(placer, false, placer, teammates))
        assertTrue(PastureAccessPolicy.mayLock(ftbMate, false, placer, teammates))
        assertTrue(PastureAccessPolicy.mayLock(opacMate, false, placer, teammates))
        assertTrue(PastureAccessPolicy.mayLock(operator, true, placer, teammates))
        assertFalse(PastureAccessPolicy.mayLock(contributor, false, placer, teammates))
        assertFalse(PastureAccessPolicy.mayLock(bystander, false, placer, teammates))

        assertTrue(PastureAccessPolicy.mayLock(operator, true, ownerId = null, teammates))
        assertFalse(PastureAccessPolicy.mayLock(contributor, false, ownerId = null, teammates))
        assertFalse(PastureAccessPolicy.mayLock(placer, false, ownerId = null, teammates))
    }
}
