package codes.honor.cobblemondynamics.engine

import kotlin.math.ceil
import kotlin.math.max

object TravelTiming {
    const val MIN_SPEED = 0.005
    const val DEFAULT_SPEED = 0.10
    const val SAFETY_FACTOR = 2.5
    const val MAX_TIMEOUT_TICKS = 6_000
    private const val RETRY_STEP_1 = 200
    private const val RETRY_STEP_2 = 600
    private const val RETRY_STEP_3 = 2_400

    fun effectiveSpeed(speed: Double): Double = if (speed.isFinite() && speed > 0.0) speed.coerceAtLeast(MIN_SPEED) else DEFAULT_SPEED

    fun timeoutTicks(baseTimeoutTicks: Int, distance: Double, speed: Double): Int {
        val effSpeed = effectiveSpeed(speed)
        val scaled = if (distance.isFinite() && distance > 0.0) ceil(distance / effSpeed * SAFETY_FACTOR).toInt() else 0
        return max(baseTimeoutTicks, scaled).coerceAtMost(MAX_TIMEOUT_TICKS)
    }

    fun retryTicks(missCount: Int, maxRetryWaitTicks: Int): Int {
        val step = when {
            missCount <= 1 -> RETRY_STEP_1
            missCount == 2 -> RETRY_STEP_2
            else -> RETRY_STEP_3
        }
        return step.coerceAtMost(maxRetryWaitTicks.coerceAtLeast(0))
    }
}
