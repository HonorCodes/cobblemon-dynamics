package codes.honor.cobblemondynamics.heal

import codes.honor.cobblemondynamics.config.EngineSettings
import codes.honor.cobblemondynamics.stamina.Stamina
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HealTransactionTest {
    private val s = EngineSettings.DEFAULT

    @Test
    fun `CD-HEAL-04 four heals read 75, 50, 25 and 0, then Tired`() {
        val healer = Stamina(1.0)
        listOf(75.0, 50.0, 25.0, 0.0).forEach {
            assertFalse(healer.tired)
            HealTransaction.complete(healer, Stamina(0.0, true), s)
            assertEquals(it, healer.percent, 1e-9)
        }
        assertTrue(healer.tired)
    }

    @Test
    fun `CD-HEAL-05 a Lv1 healer fills a Lv100 ally to 100 percent`() {
        val ally = Stamina(0.0, true)
        HealTransaction.complete(Stamina(1.0), ally, s)
        assertEquals(100.0, ally.percent, 1e-9)
        assertFalse(ally.tired)
        val low = Stamina(0.10)
        HealTransaction.complete(Stamina(0.30), low, s)
        assertEquals(100.0, low.percent, 1e-9)
    }

    @Test
    fun `CD-HEAL-10 2_0 blocks completes and 2_1 does not`() {
        assertTrue(HealTransaction.inRange(Vec3.ZERO, Vec3(2.0, 0.0, 0.0)))
        assertFalse(HealTransaction.inRange(Vec3.ZERO, Vec3(2.1, 0.0, 0.0)))
    }

    @Test
    fun `CD-HEAL-10 the distance counts to the ally's body, so a healer reaches a wide ally`() {
        // A Wailord is 8 blocks wide: its center is 5 blocks from a healer at its side.
        val wailord = AABB(-4.0, 0.0, -4.0, 4.0, 5.25, 4.0)
        assertEquals(Vec3(4.0, 0.0, 0.0), HealTransaction.nearestPoint(wailord, Vec3(5.0, 0.0, 0.0)))
        assertTrue(HealTransaction.inRange(Vec3(5.0, 0.0, 0.0), HealTransaction.nearestPoint(wailord, Vec3(5.0, 0.0, 0.0))))
        assertFalse(HealTransaction.inRange(Vec3(6.1, 0.0, 0.0), HealTransaction.nearestPoint(wailord, Vec3(6.1, 0.0, 0.0))))
    }
}
