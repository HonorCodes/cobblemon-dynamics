package codes.honor.cobblemondynamics.client

/**
 * When the client asks the server for its leash overview: on the tick that the Work Leash comes into the main hand, and every
 * 20 ticks while it stays there, so a binding made with the leash in hand gets its outline (CD-UI-09).
 */
class OverviewRequests {
    private var heldTicks = -1

    /** Call once per client tick. True when the client should send `leash_overview_request` now. */
    fun tick(holding: Boolean): Boolean {
        if (!holding) {
            heldTicks = -1
            return false
        }
        heldTicks++
        return heldTicks % REFRESH_TICKS == 0
    }

    private companion object {
        const val REFRESH_TICKS = 20
    }
}
