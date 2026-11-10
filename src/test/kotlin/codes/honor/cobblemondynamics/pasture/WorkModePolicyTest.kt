package codes.honor.cobblemondynamics.pasture

import codes.honor.cobblemondynamics.pasture.WorkModePolicy.Decision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.util.UUID

class WorkModePolicyTest {
    private val placer = UUID.randomUUID()
    private val stranger = UUID.randomUUID()
    private fun decide(
        who: UUID,
        op: Boolean = false,
        owner: UUID? = placer,
        distance: Double = 3.0,
        same: Boolean = true,
        now: Long = 100,
        last: Long? = null,
    ) = WorkModePolicy.decide(who, op, owner, distance, same, now, last, cooldownTicks = 10)

    @Test fun `CD-WMD-03 the placer is accepted`() = assertEquals(Decision.Apply, decide(placer))

    @Test fun `CD-WMD-04 an operator is accepted on any pasture`() = assertEquals(Decision.Apply, decide(stranger, op = true))

    @Test fun `CD-SEC-01 a stranger is rejected`() {
        assertInstanceOf(Decision.Reject::class.java, decide(stranger))
    }

    @Test fun `CD-SEC-02 with no placer only operators pass`() {
        assertInstanceOf(Decision.Reject::class.java, decide(stranger, owner = null))
        assertEquals(Decision.Apply, decide(stranger, op = true, owner = null))
    }

    @Test fun `CD-SEC-03 a request 9 ticks after the last accepted one is ignored, 10 ticks after is applied`() {
        assertEquals(Decision.Ignore, decide(placer, now = 109, last = 100))
        assertEquals(Decision.Apply, decide(placer, now = 110, last = 100))
    }

    @Test fun `CD-SEC-04 10_0 blocks passes, 10_1 blocks and another dimension fail`() {
        assertEquals(Decision.Apply, decide(placer, distance = 10.0))
        assertInstanceOf(Decision.Reject::class.java, decide(placer, distance = 10.1))
        assertInstanceOf(Decision.Reject::class.java, decide(placer, same = false))
    }
}
