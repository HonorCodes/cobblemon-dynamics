package codes.honor.cobblemondynamics.heal

import codes.honor.cobblemondynamics.config.EngineSettings
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class HealTargetsTest {
    private val s = EngineSettings.DEFAULT
    private val healer = UUID.randomUUID()

    private fun ally(x: Double, tired: Boolean = false, f: Double = 1.0, fairy: Boolean = false, id: UUID = UUID.randomUUID()) =
        AllyView(id, Vec3(x, 0.0, 0.0), fairy, tired, f)

    @Test
    fun `CD-HEAL-02 25_0 percent starts a heal and 24_9 does not`() {
        assertTrue(HealTargets.canStart(0.250, false, s))
        assertFalse(HealTargets.canStart(0.249, false, s))
    }

    @Test
    fun `CD-HEAL-03 a Tired healer at 60 percent does not heal`() = assertFalse(HealTargets.canStart(0.60, true, s))

    @Test
    fun `CD-HEAL-06 the filter drops the healer and Fairy allies`() {
        val self = ally(0.0, tired = true, id = healer)
        assertNull(HealTargets.pick(healer, Vec3.ZERO, listOf(self, ally(1.0, tired = true, fairy = true)), { true }, s))
    }

    @Test
    fun `CD-HEAL-07 the nearest Tired ally comes first`() {
        val t3 = ally(3.0, tired = true)
        val t6 = ally(6.0, tired = true)
        val low = ally(1.0, f = 0.10)
        assertEquals(t3.id, HealTargets.pick(healer, Vec3.ZERO, listOf(t6, low, t3), { true }, s)!!.id)
    }

    @Test
    fun `CD-HEAL-08 the lowest ally under 25 percent, and none at 25_0`() {
        val a = ally(1.0, f = 0.249)
        val b = ally(2.0, f = 0.10)
        assertEquals(b.id, HealTargets.pick(healer, Vec3.ZERO, listOf(a, b), { true }, s)!!.id)
        assertNull(HealTargets.pick(healer, Vec3.ZERO, listOf(ally(1.0, f = 0.25)), { true }, s))
    }

    @Test
    fun `CD-HEAL-08 the configured threshold sets which allies need a heal`() {
        val ally = ally(1.0, f = 0.40)
        assertNull(HealTargets.pick(healer, Vec3.ZERO, listOf(ally), { true }, s))
        assertEquals(ally.id, HealTargets.pick(healer, Vec3.ZERO, listOf(ally), { true }, s.copy(lowStaminaThreshold = 0.5))?.id)
    }

    @Test
    fun `CD-HEAL-09 an ally that another healer claimed is skipped`() {
        val t3 = ally(3.0, tired = true)
        val t6 = ally(6.0, tired = true)
        assertEquals(t6.id, HealTargets.pick(healer, Vec3.ZERO, listOf(t3, t6), { it != t3.id }, s)!!.id)
        assertNull(HealTargets.pick(healer, Vec3.ZERO, listOf(t3), { false }, s))
    }

    @Test
    fun `CD-HEAL-11 a target stops qualifying at 100 percent, when it leaves the pasture, or as a Fairy form`() {
        assertTrue(HealTargets.qualifies(ally(1.0, tired = true, f = 0.99), s))
        assertTrue(HealTargets.qualifies(ally(1.0, f = 0.10), s))
        assertFalse(HealTargets.qualifies(ally(1.0, f = 1.0), s))
        assertFalse(HealTargets.qualifies(null, s))
        assertFalse(HealTargets.qualifies(ally(1.0, tired = true, fairy = true), s))
    }
}
