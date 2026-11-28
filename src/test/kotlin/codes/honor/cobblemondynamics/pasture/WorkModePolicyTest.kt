package codes.honor.cobblemondynamics.pasture

import codes.honor.cobblemondynamics.pasture.WorkModePolicy.Decision
import com.mojang.serialization.JsonOps
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test fun `CD-UI-03 the panel may toggle only for the placer and operators`() {
        assertEquals(true, WorkModePolicy.mayToggle(placer, isOperator = false, placer = placer))
        assertEquals(true, WorkModePolicy.mayToggle(stranger, isOperator = true, placer = null))
        assertEquals(false, WorkModePolicy.mayToggle(stranger, isOperator = false, placer = placer))
        assertEquals(false, WorkModePolicy.mayToggle(stranger, isOperator = false, placer = null))
    }

    @Test fun `CD-SEC-04 10_0 blocks passes, 10_1 blocks and another dimension fail`() {
        assertEquals(Decision.Apply, decide(placer, distance = 10.0))
        assertInstanceOf(Decision.Reject::class.java, decide(placer, distance = 10.1))
        assertInstanceOf(Decision.Reject::class.java, decide(placer, same = false))
    }

    @Test fun `CD-TEAM-01 CD-TEAM-02 teammates and unlocked contributors may toggle, and locked pastures reject non-teammates`() {
        val mate = UUID.randomUUID()
        val contributor = UUID.randomUUID()
        val isMate: (UUID, UUID) -> Boolean = { a, b -> setOf(a, b) == setOf(placer, mate) }
        assertEquals(
            Decision.Apply,
            WorkModePolicy.decide(contributor, false, placer, 3.0, true, 100, null, 10, false, setOf(contributor), isMate),
        )
        assertInstanceOf(
            Decision.Reject::class.java,
            WorkModePolicy.decide(contributor, false, placer, 3.0, true, 100, null, 10, true, setOf(contributor), isMate),
        )
        assertEquals(
            Decision.Apply,
            WorkModePolicy.decide(mate, false, placer, 3.0, true, 100, null, 10, true, setOf(contributor), isMate),
        )
    }

    @Test fun `CD-TEAM-03 decideLock accepts placer, teammates and operators and rejects non-teammate contributors`() {
        val mate = UUID.randomUUID()
        val contributor = UUID.randomUUID()
        val isMate: (UUID, UUID) -> Boolean = { a, b -> setOf(a, b) == setOf(placer, mate) }
        assertEquals(Decision.Apply, WorkModePolicy.decideLock(placer, false, placer, 3.0, true, 100, null, 10, isMate))
        assertEquals(Decision.Apply, WorkModePolicy.decideLock(mate, false, placer, 3.0, true, 100, null, 10, isMate))
        assertInstanceOf(
            Decision.Reject::class.java,
            WorkModePolicy.decideLock(contributor, false, placer, 3.0, true, 100, null, 10, isMate),
        )
        assertInstanceOf(Decision.Reject::class.java, WorkModePolicy.decideLock(placer, false, null, 3.0, true, 100, null, 10, isMate))
        assertEquals(Decision.Apply, WorkModePolicy.decideLock(stranger, true, null, 3.0, true, 100, null, 10, isMate))
    }

    @Test fun `CD-TEAM-03 CD-WMD-02 WorkModeData codec persists locked and defaults missing locked to false`() {
        val legacyJson = com.google.gson.JsonParser.parseString("""{"enabled":true}""")
        val legacy = WorkModeData.CODEC.parse(JsonOps.INSTANCE, legacyJson).getOrThrow()
        assertTrue(legacy.enabled)
        assertFalse(legacy.locked)

        val lockedData = WorkModeData(enabled = false, locked = true)
        val encoded = WorkModeData.CODEC.encodeStart(JsonOps.INSTANCE, lockedData).getOrThrow()
        val decoded = WorkModeData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow()
        assertEquals(lockedData, decoded)
    }
}
