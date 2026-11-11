package codes.honor.cobblemondynamics.fx

import codes.honor.cobblemondynamics.role.EffectDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EffectClockTest {
    private fun effect(loopTicks: Int = 40, idleTicks: Int = 40) = EffectDefinition(
        loopTicks = loopTicks,
        idleTicks = idleTicks,
        animations = listOf("special"),
        actorParticles = emptyList(),
        targetParticles = emptyList(),
    )

    @Test
    fun `CD-FX-01 effects start at 0, 40 and 80 and never at 39`() {
        val plays = (0L..100L).filter { EffectClock.shouldPlay(0, it, 40) }
        assertEquals(listOf(0L, 40L, 80L), plays)
    }

    /** The approver's play-test ruling (2026-11-10): a loop that restarted a 3 s animation every 2 s looked glitchy. */
    @Test
    fun `CD-FX-01 a 3 s animation plays to its end, and 40 idle ticks pass before the next play`() {
        val period = EffectClock.periodTicks(WorkAnimation("special", 60), effect())
        assertEquals(100, period)
        assertEquals(listOf(0L, 100L, 200L), (0L..250L).filter { EffectClock.shouldPlay(0, it, period) })
    }

    @Test
    fun `CD-FX-01 the idle gap is the role file's idle_ticks`() {
        assertEquals(60, EffectClock.periodTicks(WorkAnimation("special", 60), effect(idleTicks = 0)))
        assertEquals(140, EffectClock.periodTicks(WorkAnimation("special", 60), effect(idleTicks = 80)))
    }

    @Test
    fun `CD-FX-01 a worker whose model has none of the role's animations shows its particles every loop_ticks`() {
        assertEquals(40, EffectClock.periodTicks(null, effect()))
        assertEquals(25, EffectClock.periodTicks(null, effect(loopTicks = 25, idleTicks = 90)))
    }

    @Test
    fun `an animation length rounds up to whole ticks`() {
        assertEquals(60, WorkAnimation.ticks(3.0))
        assertEquals(62, WorkAnimation.ticks(3.0833))
        assertEquals(1, WorkAnimation.ticks(0.01))
    }
}
