package codes.honor.cobblemondynamics.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OverviewRequestsTest {
    /** The ticks of [held] (one entry per client tick) on which the client asks the server for its leash overview. */
    private fun requests(held: List<Boolean>): List<Int> {
        val requests = OverviewRequests()
        return held.indices.filter { requests.tick(held[it]) }
    }

    @Test
    fun `CD-UI-09 the client asks as the leash comes into the main hand, then every 20 ticks while it stays there`() {
        assertEquals(listOf(2, 22, 42), requests(List(2) { false } + List(45) { true }))
    }

    @Test
    fun `putting the leash away and taking it again asks at once`() {
        assertEquals(listOf(0, 7), requests(List(5) { true } + List(2) { false } + List(3) { true }))
    }

    @Test
    fun `without the leash the client never asks`() {
        assertEquals(emptyList<Int>(), requests(List(60) { false }))
    }
}
