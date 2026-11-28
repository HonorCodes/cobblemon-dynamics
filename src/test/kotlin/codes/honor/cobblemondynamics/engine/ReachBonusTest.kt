package codes.honor.cobblemondynamics.engine

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReachBonusTest {
    @Test
    fun `CD-NAV-04 sizeBonus matches narrow, wide and Alpha workers`() {
        assertEquals(0.0, Reach.sizeBonus(bbWidth = 0.8, isAlpha = false), 1e-6)
        assertEquals(0.0, Reach.sizeBonus(bbWidth = 1.0, isAlpha = false), 1e-6)
        assertEquals(0.85, Reach.sizeBonus(bbWidth = 2.0, isAlpha = false), 1e-6)
        assertEquals(1.0, Reach.sizeBonus(bbWidth = 0.9, isAlpha = true), 1e-6)
        assertEquals(2.275, Reach.sizeBonus(bbWidth = 2.5, isAlpha = true), 1e-6)
        assertEquals(3.85, Reach.effectiveRange(2.0, bbWidth = 2.0, isAlpha = true), 1e-6)
    }

    @Test
    fun `canPlanNow matches OmniPathNavigation canUpdatePath across water-swimmer and non-swimmer branches`() {
        assertFalse(Reach.canPlanNow(canSwimInWater = true, onGround = false, isFlying = false))
        assertTrue(Reach.canPlanNow(canSwimInWater = true, onGround = true, isFlying = false))
        assertTrue(Reach.canPlanNow(canSwimInWater = true, onGround = false, isFlying = true))
        assertTrue(Reach.canPlanNow(canSwimInWater = true, onGround = false, inWaterBox = true))
        assertTrue(Reach.canPlanNow(canSwimInWater = true, onGround = false, isInLiquid = true))
        assertTrue(Reach.canPlanNow(canSwimInWater = true, onGround = false, isPassenger = true))

        assertTrue(Reach.canPlanNow(canSwimInWater = false, onGround = false))
        assertFalse(
            Reach.canPlanNow(
                canSwimInWater = false,
                onGround = true,
                isInLava = true,
                inLavaBox = true,
                canSwimInLava = false,
            ),
        )
        assertFalse(
            Reach.canPlanNow(
                canSwimInWater = false,
                onGround = false,
                eyeInLava = true,
                inLavaBox = true,
                canSwimInLava = false,
            ),
        )
        assertTrue(
            Reach.canPlanNow(
                canSwimInWater = false,
                onGround = false,
                isInLava = true,
                inLavaBox = true,
                canSwimInLava = true,
            ),
        )
        assertTrue(
            Reach.canPlanNow(
                canSwimInWater = false,
                onGround = false,
                isInLava = true,
                isPassenger = true,
            ),
        )

        val check = Reach.Check(Vec3(0.5, 2.0, 0.5), 32.0, initiallyBlocked = true) { true }
        assertFalse(check(BlockPos(2, 1, 0)))
        assertFalse(check(BlockPos(40, 1, 0)))
        assertTrue(check.cannotPlan)
    }
}
