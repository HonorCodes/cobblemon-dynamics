package codes.honor.cobblemondynamics.ai

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.abs

class LookMathTest {
    @Test
    fun `CD-AI-05 facing south is 0 degrees`() = assertEquals(0f, LookMath.yawTowards(Vec3.ZERO, Vec3(0.0, 0.0, 5.0)), 0.01f)

    @Test
    fun `CD-AI-05 facing west is 90 degrees`() = assertEquals(90f, LookMath.yawTowards(Vec3.ZERO, Vec3(-5.0, 0.0, 0.0)), 0.01f)

    @Test
    fun `CD-AI-05 facing north is 180 degrees`() = assertEquals(180f, abs(LookMath.yawTowards(Vec3.ZERO, Vec3(0.0, 0.0, -5.0))), 0.01f)
}
