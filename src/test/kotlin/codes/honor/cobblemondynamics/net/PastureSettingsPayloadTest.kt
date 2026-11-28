package codes.honor.cobblemondynamics.net

import codes.honor.cobblemondynamics.pasture.WorkModePolicy
import codes.honor.cobblemondynamics.pasture.WorkModePolicy.Decision
import io.netty.buffer.Unpooled
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class PastureSettingsPayloadTest {
    private fun roundTrip(payload: PastureSettingsPayload): PastureSettingsPayload {
        val buf = Unpooled.buffer()
        PastureSettingsPayload.STREAM_CODEC.encode(buf, payload)
        val decoded = PastureSettingsPayload.STREAM_CODEC.decode(buf)
        assertEquals(0, buf.readableBytes())
        return decoded
    }

    @Test
    fun `CD-SHF-02 CD-SHF-03 settings payload round-trips radius lock and combined updates`() {
        val payloads = listOf(
            PastureSettingsPayload(radius = 4),
            PastureSettingsPayload(radius = 16),
            PastureSettingsPayload(locked = true),
            PastureSettingsPayload(radius = 8, locked = false),
        )
        for (payload in payloads) {
            assertTrue(payload.isValid(16))
            assertEquals(payload, roundTrip(payload))
        }
        assertFalse(PastureSettingsPayload().isValid(16))
        assertFalse(PastureSettingsPayload(radius = 7).isValid(16))
        assertFalse(PastureSettingsPayload(radius = 16).isValid(12))
    }

    @Test
    fun `CD-SHF-02 CD-SHF-03 decideRadius validates step cap authorization distance dimension and cooldown`() {
        val placer = UUID.randomUUID()
        val contributor = UUID.randomUUID()
        val stranger = UUID.randomUUID()

        assertEquals(listOf(4, 8, 12, 16), WorkModePolicy.allowedRadiusSteps(16))
        assertEquals(listOf(4, 8, 12), WorkModePolicy.allowedRadiusSteps(14))
        assertEquals(listOf(4), WorkModePolicy.allowedRadiusSteps(5))

        assertEquals(
            Decision.Apply,
            WorkModePolicy.decideRadius(
                requester = contributor,
                isOperator = false,
                placer = placer,
                radius = 12,
                maxHorizontalRadius = 16,
                distance = 5.0,
                sameDimension = true,
                now = 100L,
                lastAccepted = null,
                cooldownTicks = 10,
                locked = false,
                tetheredOwners = setOf(contributor),
            ),
        )

        assertEquals(
            Decision.Reject("invalid_radius"),
            WorkModePolicy.decideRadius(
                requester = placer,
                isOperator = false,
                placer = placer,
                radius = 10,
                maxHorizontalRadius = 16,
                distance = 2.0,
                sameDimension = true,
                now = 100L,
                lastAccepted = null,
                cooldownTicks = 10,
            ),
        )

        assertEquals(
            Decision.Reject("invalid_radius"),
            WorkModePolicy.decideRadius(
                requester = placer,
                isOperator = false,
                placer = placer,
                radius = 16,
                maxHorizontalRadius = 12,
                distance = 2.0,
                sameDimension = true,
                now = 100L,
                lastAccepted = null,
                cooldownTicks = 10,
            ),
        )

        assertEquals(
            Decision.Reject("not_owner"),
            WorkModePolicy.decideRadius(
                requester = contributor,
                isOperator = false,
                placer = placer,
                radius = 8,
                maxHorizontalRadius = 16,
                distance = 2.0,
                sameDimension = true,
                now = 100L,
                lastAccepted = null,
                cooldownTicks = 10,
                locked = true,
                tetheredOwners = setOf(contributor),
            ),
        )

        assertEquals(
            Decision.Reject("not_owner"),
            WorkModePolicy.decideRadius(
                requester = stranger,
                isOperator = false,
                placer = placer,
                radius = 8,
                maxHorizontalRadius = 16,
                distance = 2.0,
                sameDimension = true,
                now = 100L,
                lastAccepted = null,
                cooldownTicks = 10,
                locked = false,
                tetheredOwners = setOf(contributor),
            ),
        )

        assertEquals(
            Decision.Reject("too_far"),
            WorkModePolicy.decideRadius(
                requester = placer,
                isOperator = false,
                placer = placer,
                radius = 8,
                maxHorizontalRadius = 16,
                distance = 10.1,
                sameDimension = true,
                now = 100L,
                lastAccepted = null,
                cooldownTicks = 10,
            ),
        )

        assertEquals(
            Decision.Ignore,
            WorkModePolicy.decideRadius(
                requester = placer,
                isOperator = false,
                placer = placer,
                radius = 8,
                maxHorizontalRadius = 16,
                distance = 2.0,
                sameDimension = true,
                now = 105L,
                lastAccepted = 100L,
                cooldownTicks = 10,
            ),
        )
    }
}
