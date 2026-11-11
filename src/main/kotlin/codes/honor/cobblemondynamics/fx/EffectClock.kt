package codes.honor.cobblemondynamics.fx

import codes.honor.cobblemondynamics.role.EffectDefinition

object EffectClock {
    /** True on [startTick] and on every [periodTicks]-th tick after it (CD-FX-01). */
    fun shouldPlay(startTick: Long, now: Long, periodTicks: Int): Boolean =
        periodTicks > 0 && now >= startTick && (now - startTick) % periodTicks == 0L

    /**
     * CD-FX-01: a play lasts as long as the worker's [animation], and the worker then stays idle for the role's `idle_ticks`, so that its
     * model returns to its idle pose before the next play. A worker whose model has none of the role's animations shows the particles
     * every `loop_ticks`.
     */
    fun periodTicks(animation: WorkAnimation?, effect: EffectDefinition): Int =
        if (animation == null) effect.loopTicks else animation.lengthTicks + effect.idleTicks
}
