package codes.honor.cobblemondynamics.compat.immersiveengineering

import codes.honor.cobblemondynamics.role.BuiltInRoles
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.core.BlockPos
import net.minecraft.nbt.NbtOps
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IeWatermillRuleTest {
    private val virtual = IeWatermillRule.OPTIMIZED_OVERSHOT_TORQUE

    @Test
    fun `CD-KIN-02 a driven dry wheel reports the virtual torque times its stored direction`() {
        assertEquals(virtual, IeWatermillRule.torque(0.0, driven = true, virtual = virtual, direction = 1))
        assertEquals(-virtual, IeWatermillRule.torque(0.0, driven = true, virtual = virtual, direction = -1))
        assertEquals(0.0, IeWatermillRule.torque(0.0, driven = false, virtual = virtual, direction = 1))
    }

    @Test
    fun `CD-KIN-03 real flow wins, and only a wheel with no real torque has demand`() {
        assertEquals(12.5, IeWatermillRule.torque(12.5, driven = true, virtual = virtual, direction = -1))
        assertEquals(-3.0, IeWatermillRule.torque(-3.0, driven = true, virtual = virtual, direction = 1))
        assertTrue(IeWatermillRule.hasDemand(0.0))
        assertFalse(IeWatermillRule.hasDemand(12.5))
        assertFalse(IeWatermillRule.hasDemand(-0.01))
    }

    @Test
    fun `CD-KIN-04 the direction saves and loads, and a flip reverses it`() {
        val drive = WatermillDrive()
        assertEquals(1, drive.direction, "a new watermill turns forward")
        drive.flip()
        assertEquals(-1, drive.direction)
        drive.driven = true
        val saved = WatermillDrive.CODEC.encodeStart(NbtOps.INSTANCE, drive).getOrThrow()
        val loaded = WatermillDrive.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow()
        assertEquals(-1, loaded.direction)
        // Claims are never saved (CD-CLM-07), so a loaded wheel is never driven.
        assertFalse(loaded.driven)
        drive.flip()
        assertEquals(1, drive.direction)
        // Only a reversed wheel writes its attachment, so a save of a forward wheel holds nothing.
        assertFalse(WatermillDrive.SAVED.test(WatermillDrive()))
        assertTrue(WatermillDrive.SAVED.test(WatermillDrive().also { it.flip() }))
    }

    @Test
    fun `CD-KIN-04 a corrupt direction reads as forward`() {
        val loaded = WatermillDrive.CODEC.parse(NbtOps.INSTANCE, NbtOps.INSTANCE.createInt(7)).getOrThrow()
        assertEquals(1, loaded.direction)
    }

    @Test
    fun `CD-KIN-02 CD-BAL-14 the role file's torque never passes an optimized overshot wheel`() {
        val params = WatermillParams.of(BuiltInRoles.parse("wheel_driver_immersiveengineering").params)
        assertEquals(virtual, params.virtualTorque)
        val pack = JsonParser.parseString("""{ "virtual_torque": 500.0 }""").asJsonObject
        assertEquals(virtual, WatermillParams.of(pack).virtualTorque)
        assertEquals(10.0, WatermillParams.of(JsonParser.parseString("""{ "virtual_torque": 10 }""").asJsonObject).virtualTorque)
        assertEquals(virtual, WatermillParams.of(JsonObject()).virtualTorque)
    }

    @Test
    fun `CD-CLM-04 a dummy of the watermill's ring maps to its master`() {
        val master = BlockPos(0, 70, 0)
        assertEquals(master, IeWatermillRule.canonical(BlockPos(2, 72, 0), master))
        assertEquals(BlockPos(2, 72, 0), IeWatermillRule.canonical(BlockPos(2, 72, 0), null))
    }
}
