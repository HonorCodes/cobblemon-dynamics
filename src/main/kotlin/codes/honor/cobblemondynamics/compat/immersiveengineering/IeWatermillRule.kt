package codes.honor.cobblemondynamics.compat.immersiveengineering

import com.google.gson.JsonObject
import com.mojang.serialization.Codec
import net.minecraft.core.BlockPos
import java.util.function.Predicate

/**
 * The Wheel Driver's rules on the IE watermill. IE sums the torque of up to 3 wheels in a row on one Kinetic Dynamo, which gives as
 * many FE/t as it gets rotation, and has no clamp of its own. A driven wheel with no real torque reports the role file's torque
 * times its direction, and real flow always wins (CD-KIN-02, CD-KIN-03). No IE class, so unit tests load it.
 */
object IeWatermillRule {
    /**
     * CD-BAL-14: IE's top for one wheel, the torque of an optimized overshot wheel, which no role file passes. About 29 by the FTB
     * wiki for an older IE (RES10): the compat run of Task 23 measures it on 12.4.2-194.
     */
    const val OPTIMIZED_OVERSHOT_TORQUE = 29.0

    fun torque(real: Double, driven: Boolean, virtual: Double, direction: Int): Double {
        val dry = real == 0.0
        return if (driven && dry) virtual * direction else real
    }

    fun hasDemand(real: Double): Boolean = real == 0.0

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

    var driven = false

    /** The torque that a driven wheel reports, from its worker's role file. */
    var torque = 0.0

    /** IE's own torque of the wheel at the last call of the hook, which CD-KIN-03 reads. */
    var realTorque = 0.0

    /** The first game tick on which the handler may make IE compute the torque again. */
    var nextCheck = 0L

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
