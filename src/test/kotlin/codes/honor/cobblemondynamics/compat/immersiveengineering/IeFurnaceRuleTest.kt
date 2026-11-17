package codes.honor.cobblemondynamics.compat.immersiveengineering

import codes.honor.cobblemondynamics.compat.immersiveengineering.IeFurnaceRule.Kind
import codes.honor.cobblemondynamics.compat.immersiveengineering.IeFurnaceRule.Slot
import codes.honor.cobblemondynamics.role.BuiltInRoles
import com.google.gson.JsonObject
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IeFurnaceRuleTest {
    private fun ie(path: String) = ResourceLocation.fromNamespaceAndPath("immersiveengineering", path)

    @Test
    fun `CD-FUEL-01 CD-FUEL-02 the Stoker knows the kiln and the two blast furnaces of IE by their multiblock ids`() {
        assertEquals(Kind.ALLOY_KILN, IeFurnaceRule.kindOf(ie("alloy_smelter")))
        assertEquals(Kind.BLAST_FURNACE, IeFurnaceRule.kindOf(ie("blast_furnace")))
        assertEquals(Kind.IMPROVED_BLAST_FURNACE, IeFurnaceRule.kindOf(ie("advanced_blast_furnace")))
        // The coke oven has no fuel slot, so a worker has nothing to fuel there.
        assertNull(IeFurnaceRule.kindOf(ie("coke_oven")))
    }

    @Test
    fun `CD-FUEL-01 the recipe check follows IE's output-room rule for given stacks`() {
        val empty = Slot(empty = true, sameItem = false, count = 0, adding = 1, limit = 64)
        assertTrue(IeFurnaceRule.fits(listOf(empty)), "an empty output slot takes any result")
        assertTrue(IeFurnaceRule.fits(listOf(Slot(empty = false, sameItem = true, count = 63, adding = 1, limit = 64))))
        assertFalse(IeFurnaceRule.fits(listOf(Slot(empty = false, sameItem = true, count = 64, adding = 1, limit = 64))))
        assertFalse(IeFurnaceRule.fits(listOf(Slot(empty = false, sameItem = false, count = 1, adding = 1, limit = 64))))
        // A blast furnace needs room in its output slot and in its slag slot.
        val fullSlag = Slot(empty = false, sameItem = true, count = 64, adding = 1, limit = 64)
        assertFalse(IeFurnaceRule.fits(listOf(empty, fullSlag)))
        // IE reads the slot limit of its inventory, not the stack size of the item.
        assertTrue(IeFurnaceRule.fits(listOf(Slot(empty = false, sameItem = true, count = 70, adding = 2, limit = 72))))
    }

    @Test
    fun `CD-FUEL-01 the burn values are 2 for a kiln and a crude furnace and 4 for an improved furnace with two preheaters`() {
        assertEquals(2, IeFurnaceRule.burnValue(Kind.ALLOY_KILN, preheaters = 0))
        assertEquals(2, IeFurnaceRule.burnValue(Kind.BLAST_FURNACE, preheaters = 0))
        assertEquals(4, IeFurnaceRule.burnValue(Kind.IMPROVED_BLAST_FURNACE, preheaters = 2))
        assertEquals(3, IeFurnaceRule.burnValue(Kind.IMPROVED_BLAST_FURNACE, preheaters = 1))
        assertEquals(2, IeFurnaceRule.burnValue(Kind.IMPROVED_BLAST_FURNACE, preheaters = 0))
    }

    @Test
    fun `CD-FUEL-02 a level 49 worker gets no blast furnace, a level 50 worker does, and both get a kiln`() {
        val minLevel = IeFurnaceParams.of(JsonObject()).blastFurnaceMinLevel
        assertEquals(50, minLevel)
        for (furnace in listOf(Kind.BLAST_FURNACE, Kind.IMPROVED_BLAST_FURNACE)) {
            assertFalse(IeFurnaceRule.admits(furnace, level = 49, minLevel = minLevel), "$furnace at level 49")
            assertTrue(IeFurnaceRule.admits(furnace, level = 50, minLevel = minLevel), "$furnace at level 50")
        }
        assertTrue(IeFurnaceRule.admits(Kind.ALLOY_KILN, level = 49, minLevel = minLevel))
        assertTrue(IeFurnaceRule.admits(Kind.ALLOY_KILN, level = 1, minLevel = minLevel))
    }

    @Test
    fun `CD-FUEL-02 the default role file gates the blast furnaces at level 50`() {
        val params = BuiltInRoles.parse("stoker_immersiveengineering").params
        assertEquals(50, params.get("blast_furnace_min_level")?.asInt)
        assertEquals(IeFurnaceParams(50), IeFurnaceParams.of(params))
    }

    @Test
    fun `CD-FUEL-03 the canonical of a dummy block is the master that its helper names`() {
        val master = BlockPos(10, 64, 10)
        val dummy = BlockPos(11, 65, 9)
        assertEquals(master, IeFurnaceRule.canonical(dummy, master))
        assertEquals(master, IeFurnaceRule.canonical(master, master))
        // A dummy whose master is not loaded keys itself, and no worker gets it, because it has no state.
        assertEquals(dummy, IeFurnaceRule.canonical(dummy, null))
    }

    /** IE takes a fuel item only when its tick, which burns the process speed, leaves `burnTime` at 0 or below (RES10). */
    @Test
    fun `CD-FUEL-13 a worked furnace never reaches IE's fuel step, at any speed its preheaters give`() {
        for (kind in Kind.entries) {
            for (preheaters in 0..2) {
                val value = IeFurnaceRule.burnValue(kind, preheaters)
                val topSpeed = if (kind == Kind.IMPROVED_BLAST_FURNACE) 1 + preheaters else 1
                for (speed in 1..topSpeed) assertTrue(value - speed > 0, "$kind with $preheaters preheaters at speed $speed")
            }
        }
    }

    @Test
    fun `CD-FUEL-14 the burn that a worker leaves runs out within 2 ticks, at any speed`() {
        for (written in 2..4) {
            for (speed in 1..3) {
                val left = IeFurnaceRule.afterStop(written, written) - 2 * speed
                assertTrue(left <= 0, "a burn of $written at speed $speed leaves $left")
            }
        }
        // Fuel that the furnace took before the worker came keeps its own value.
        assertEquals(1_600, IeFurnaceRule.afterStop(1_600, written = 4))
    }
}
