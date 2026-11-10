package codes.honor.cobblemondynamics.ai

import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import kotlin.math.PI

object LookMath {
    /** The yaw from [from] to [to] in degrees, as Minecraft counts it: 0 faces south (+z), 90 faces west (-x). */
    fun yawTowards(from: Vec3, to: Vec3): Float = Mth.wrapDegrees((Mth.atan2(to.z - from.z, to.x - from.x) * 180.0 / PI).toFloat() - 90f)
}
