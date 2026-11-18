package codes.honor.cobblemondynamics.compat.immersiveengineering

import com.google.gson.JsonObject
import com.mojang.serialization.Codec
import net.minecraft.core.BlockPos
import java.util.function.Predicate

/** What the Wheel Driver reads of one cell along a watermill's axis (CD-KIN-03). */
enum class RowCell {
    /** A block that exposes IE's `IRotationAcceptor.CAPABILITY` on its face toward the wheel, such as a Kinetic Dynamo. */
    ROTATION_ACCEPTOR,

    /** A watermill that is not blocked. */
    WHEEL,

    /** Anything else, a blocked watermill included, or an unloaded cell. */
    OTHER,
}

/**
 * The Wheel Driver's rules on the IE watermill. IE sums the torque of up to 3 wheels in a row on one Kinetic Dynamo, which gives as
 * many FE/t as it gets rotation, and has no clamp of its own. A driven wheel with no real torque reports the role file's torque
 * times its direction, and real flow always wins (CD-KIN-02, CD-KIN-03). No IE class, so unit tests load it.
 */
object IeWatermillRule {
    /**
     * CD-BAL-14: IE's top for one wheel, the torque of an optimized overshot wheel, which no role file passes, in the units of
     * `getIFScaledTorque()`. The compat run measured it on IE 12.4.2-194 with real water: a raw torque of 23.60, scaled 29.50, which a
     * Kinetic Dynamo turns into 29 FE/t.
     */
    const val OPTIMIZED_OVERSHOT_TORQUE = 29.5

    /** IE's `handleUpdate` sums a wheel beside the dynamo and the next 2 wheels away from it. */
    private const val ROW = 3

    fun torque(real: Double, driven: Boolean, virtual: Double, direction: Int): Double {
        val dry = real == 0.0
        return if (driven && dry) virtual * direction else real
    }

    /** CD-KIN-03: demand while IE finds no torque of real water, and a rotation acceptor in the wheel's row takes its power. */
    fun hasDemand(real: Double, powered: Boolean): Boolean = powered && real == 0.0

    /**
     * CD-KIN-03: IE turns a Kinetic Dynamo only from the wheel beside it, which sums itself and the next 2 wheels of its row up to a
     * blocked wheel or another block. So a wheel makes power when a rotation acceptor sits at most [ROW] cells along its axis on one
     * side, past unblocked wheels only. [cell] gives the cell at a distance of 1, 2 or 3 on that side.
     */
    fun reachesAcceptor(cell: (Int) -> RowCell): Boolean {
        for (distance in 1..ROW) {
            when (cell(distance)) {
                RowCell.ROTATION_ACCEPTOR -> return true
                RowCell.WHEEL -> continue
                RowCell.OTHER -> return false
            }
        }
        return false
    }

    /** A drive holds for this tick and the next. Its worker renews it on every Working tick (CD-KIN-02). */
    fun until(now: Long): Long = now + 1

    fun holds(until: Long, now: Long): Boolean = now <= until

    /** CD-CLM-04: each block of the 5 × 5 ring keys the master at its axle. A dummy whose master is not loaded keys itself. */
    fun canonical(pos: BlockPos, master: BlockPos?): BlockPos = master ?: pos
}

/**
 * The drive of one IE watermill, on its master. [direction] is saved with the wheel (CD-KIN-04). The rest belongs to the worker's
 * claim, which is never saved (CD-CLM-07), so a loaded wheel is never driven.
 */
class WatermillDrive(direction: Int = FORWARD) {
    var direction: Int = if (direction < 0) -FORWARD else FORWARD
        private set

    /** True from the worker's start to the end of its claim, or until the worker has not renewed the drive for 2 ticks. */
    var driven = false
        private set

    /** The torque that a driven wheel reports, from its worker's role file. */
    var torque = 0.0
        private set

    /** The last game tick that the drive holds with no renewal. */
    private var until = Long.MIN_VALUE

    /** IE's own torque of the wheel at the last call of the hook, which CD-KIN-03 reads. */
    var realTorque = 0.0

    /** CD-KIN-03: at the last check, a rotation acceptor sat in the wheel's row. */
    var powered = false

    /** The first game tick on which the handler may make IE compute the torque again. */
    var nextCheck = 0L

    /** The worker starts the drive on [now] with the torque of its role file. */
    fun drive(now: Long, torque: Double) {
        driven = true
        this.torque = torque
        until = IeWatermillRule.until(now)
    }

    /** The worker renews a drive that holds on each Working tick. A lapsed drive stays off until the worker starts it again. */
    fun renew(now: Long) {
        if (driven) until = IeWatermillRule.until(now)
    }

    /** Whether the drive holds on [now]. A drive that its worker did not renew ends here, at IE's next update of the wheel. */
    fun holds(now: Long): Boolean {
        if (driven && !IeWatermillRule.holds(until, now)) driven = false
        return driven
    }

    /** Ends the drive at once. True when it was driven, so that IE must compute the wheel again. */
    fun release(): Boolean {
        val was = driven
        driven = false
        return was
    }

    fun flip() {
        direction = -direction
    }

    companion object {
        const val FORWARD = 1

        val CODEC: Codec<WatermillDrive> = Codec.INT.xmap({ WatermillDrive(it) }, { it.direction })

        /** Only a reversed wheel writes its drive, so a save of a forward wheel holds nothing of ours. */
        val SAVED: Predicate<WatermillDrive> = Predicate { it.direction != FORWARD }
    }
}

/** `virtual_torque` of `wheel_driver_immersiveengineering.json`, at most an optimized overshot wheel (CD-BAL-14). */
data class WatermillParams(val virtualTorque: Double) {
    companion object {
        private const val VIRTUAL_TORQUE = "virtual_torque"

        fun of(params: JsonObject): WatermillParams {
            val value = params.get(VIRTUAL_TORQUE)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble
            return WatermillParams(
                (value ?: IeWatermillRule.OPTIMIZED_OVERSHOT_TORQUE).coerceIn(0.0, IeWatermillRule.OPTIMIZED_OVERSHOT_TORQUE),
            )
        }
    }
}
