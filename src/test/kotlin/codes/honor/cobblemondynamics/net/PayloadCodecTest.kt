package codes.honor.cobblemondynamics.net

import codes.honor.cobblemondynamics.engine.WorkerStatus
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import net.minecraft.core.BlockPos
import net.minecraft.network.codec.StreamCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.util.UUID

/** Every payload uses its plain `StreamCodec<ByteBuf, ...>`, because none carries registry data. */
class PayloadCodecTest {
    private fun <T> roundTrip(codec: StreamCodec<ByteBuf, T>, value: T): T {
        val buffer = Unpooled.buffer()
        codec.encode(buffer, value)
        val decoded = codec.decode(buffer)
        assertEquals(0, buffer.readableBytes(), "bytes left after decoding $value")
        return decoded
    }

    @Test
    fun `CD-SEC-05 the toggle payload carries the requested state, never a flip`() {
        for (enabled in listOf(true, false)) {
            assertEquals(WorkModeTogglePayload(enabled), roundTrip(WorkModeTogglePayload.STREAM_CODEC, WorkModeTogglePayload(enabled)))
        }
    }

    @Test
    fun `CD-SEC-06 the panel state payload keeps every entry and its state`() {
        val payload = PasturePanelStatePayload(
            workMode = true,
            canToggle = false,
            entries = listOf(
                PanelEntry(UUID.randomUUID(), 100, WorkerStatus.WORKING),
                PanelEntry(UUID.randomUUID(), 42, WorkerStatus.STANDBY),
            ),
        )
        assertEquals(payload, roundTrip(PasturePanelStatePayload.STREAM_CODEC, payload))
    }

    @Test
    fun `the leash overview payload keeps each worker and target`() {
        val payload = LeashOverviewPayload(listOf(17 to BlockPos(3, 64, -9), -1 to BlockPos(-30_000, 5, 12)))
        assertEquals(payload, roundTrip(LeashOverviewPayload.STREAM_CODEC, payload))
    }

    @Test
    fun `the payloads with no fields decode to themselves`() {
        assertSame(PasturePanelOpenPayload, roundTrip(PasturePanelOpenPayload.STREAM_CODEC, PasturePanelOpenPayload))
        assertSame(PasturePanelClosePayload, roundTrip(PasturePanelClosePayload.STREAM_CODEC, PasturePanelClosePayload))
        assertSame(LeashOverviewRequestPayload, roundTrip(LeashOverviewRequestPayload.STREAM_CODEC, LeashOverviewRequestPayload))
    }
}
