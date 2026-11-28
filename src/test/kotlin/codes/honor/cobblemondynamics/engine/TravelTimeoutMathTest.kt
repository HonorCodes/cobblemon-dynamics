package codes.honor.cobblemondynamics.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TravelTimeoutMathTest {
    @Test
    fun `CD-NAV-08 scales timeout by distance and speed with 0_005 floor, 0_10 fallback and 6000 cap`() {
        assertEquals(600, TravelTiming.timeoutTicks(600, distance = 6.0, speed = 0.15))
        assertEquals(3_334, TravelTiming.timeoutTicks(600, distance = 16.0, speed = 0.012))
        assertEquals(6_000, TravelTiming.timeoutTicks(600, distance = 16.0, speed = 0.001))
        assertEquals(750, TravelTiming.timeoutTicks(600, distance = 30.0, speed = 0.0))
        assertEquals(750, TravelTiming.timeoutTicks(600, distance = 30.0, speed = -0.2))
        assertEquals(750, TravelTiming.timeoutTicks(600, distance = 30.0, speed = Double.NaN))
        assertEquals(750, TravelTiming.timeoutTicks(600, distance = 30.0, speed = Double.POSITIVE_INFINITY))
        assertEquals(1_200, TravelTiming.timeoutTicks(1_200, distance = 6.0, speed = 0.15))
    }
}
