package codes.honor.cobblemondynamics.compat.create_enchantment_industry

import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CeiInfuserRuleTest {
    /** CEI 2.5.4b (javap): `updateInfusionStats` gathers at `getBlockPos().below()`, and `getBasin` reads the block entity at `below(2)`. */
    @Test
    fun `CD-MCH-06 the Infuser gathers its stats one block below itself and finds its basin two blocks below`() {
        val infuser = BlockPos(4, 70, -9)
        assertEquals(BlockPos(4, 69, -9), CeiInfuserRule.statsPos(infuser))
        assertEquals(BlockPos(4, 68, -9), CeiInfuserRule.basinPos(infuser))
    }

    /** CEI gathers the stats only while the Infuser is idle, so the demand is the assembled Infuser and not a running infusion. */
    @Test
    fun `CD-MCH-06 the Muse has demand while the Infuser stands over its basin, and none without the basin`() {
        assertTrue(CeiInfuserRule.hasDemand(basinBelow = true))
        assertFalse(CeiInfuserRule.hasDemand(basinBelow = false))
    }
}
