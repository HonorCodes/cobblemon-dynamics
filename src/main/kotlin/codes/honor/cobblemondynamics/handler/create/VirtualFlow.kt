package codes.honor.cobblemondynamics.handler.create

import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity

/** The flow that a Working Wheel Driver gives a wheel. The water-wheel mixin uses it when real fluid gives the wheel none. */
object VirtualFlow {
    /** −1, 0 or +1: the wheel's direction while a worker drives it, else 0. */
    @JvmStatic
    fun sign(wheel: WaterWheelBlockEntity): Int = DriveBehaviour.of(wheel)?.takeIf { it.driven }?.direction ?: 0
}
