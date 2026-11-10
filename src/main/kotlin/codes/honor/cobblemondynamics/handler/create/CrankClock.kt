package codes.honor.cobblemondynamics.handler.create

/** When a Cranker turns its crank. Create stops a hand crank 10 ticks after its last turn, so every 5 ticks never lets it stop. */
object CrankClock {
    const val INTERVAL_TICKS = 5L

    /** CD-CRK-02: on the tick that the drive starts, and every 5 ticks after it. */
    fun shouldTurn(drivenSince: Long, now: Long): Boolean = now >= drivenSince && (now - drivenSince) % INTERVAL_TICKS == 0L
}
