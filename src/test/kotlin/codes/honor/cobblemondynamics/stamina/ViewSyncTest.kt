package codes.honor.cobblemondynamics.stamina

import codes.honor.cobblemondynamics.engine.WorkerStatus
import io.netty.buffer.Unpooled
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ViewSyncTest {
    @Test
    fun `CD-STA-15 0_9 points sends nothing, 1_0 sends one, a state change sends one`() {
        assertFalse(ViewSync.shouldSync(50.0, 50.9, WorkerStatus.WORKING, WorkerStatus.WORKING))
        assertTrue(ViewSync.shouldSync(50.0, 51.0, WorkerStatus.WORKING, WorkerStatus.WORKING))
        assertTrue(ViewSync.shouldSync(50.0, 50.1, WorkerStatus.WORKING, WorkerStatus.STANDBY))
    }

    @Test
    fun `CD-STA-15 a drain counts like a recovery, and no change sends nothing`() {
        assertTrue(ViewSync.shouldSync(50.0, 49.0, WorkerStatus.WORKING, WorkerStatus.WORKING))
        assertFalse(ViewSync.shouldSync(50.0, 49.1, WorkerStatus.WORKING, WorkerStatus.WORKING))
        assertFalse(ViewSync.shouldSync(50.0, 50.0, WorkerStatus.IDLE, WorkerStatus.IDLE))
    }

    /** Stamina values come from repeated one-tick steps, so one point is often 0.99999999 when it is read back. */
    @Test
    fun `CD-STA-15 one point read back through rounding still sends one`() {
        assertTrue(ViewSync.shouldSync(0.28 * 100, 0.29 * 100, WorkerStatus.WORKING, WorkerStatus.WORKING))
    }

    @Test
    fun `CD-STA-15 the view rounds down, so it reads 100 only at full`() {
        assertEquals(StaminaView(99, WorkerStatus.SLEEPING), StaminaView.of(Stamina(0.999, tired = true), WorkerStatus.SLEEPING))
        assertEquals(StaminaView(100, WorkerStatus.IDLE), StaminaView.of(Stamina(1.0), WorkerStatus.IDLE))
        assertEquals(StaminaView(0, WorkerStatus.TO_BED), StaminaView.of(Stamina(0.0, tired = true), WorkerStatus.TO_BED))
        assertEquals(29, StaminaView.displayPercent(0.29 * 100))
    }

    @Test
    fun `CD-STA-15 the view codec keeps the percent and every state`() {
        for (state in WorkerStatus.entries) {
            val view = StaminaView(42, state)
            val buffer = Unpooled.buffer()
            StaminaView.STREAM_CODEC.encode(buffer, view)
            assertEquals(view, StaminaView.STREAM_CODEC.decode(buffer))
            assertEquals(0, buffer.readableBytes(), "bytes left after decoding $view")
        }
    }
}
