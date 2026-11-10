package codes.honor.cobblemondynamics.fx

object EffectClock {
    /** True on [startTick] and on every [loopTicks]-th tick after it (CD-FX-01). */
    fun shouldPlay(startTick: Long, now: Long, loopTicks: Int): Boolean =
        loopTicks > 0 && now >= startTick && (now - startTick) % loopTicks == 0L
}
