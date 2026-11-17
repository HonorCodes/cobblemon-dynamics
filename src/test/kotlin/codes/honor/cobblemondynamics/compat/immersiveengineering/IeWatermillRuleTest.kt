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
        assertTrue(IeWatermillRule.hasDemand(0.0, powered = true))
        assertFalse(IeWatermillRule.hasDemand(12.5, powered = true))
        assertFalse(IeWatermillRule.hasDemand(-0.01, powered = true))
    }

    /** The cells along one side of the wheel's axis, from the cell beside it, and OTHER past their end. */
    private fun row(vararg cells: RowCell): (Int) -> RowCell = { distance -> cells.getOrElse(distance - 1) { RowCell.OTHER } }

    /**
     * IE's `handleUpdate` turns the Kinetic Dynamo from the wheel beside it, and that wheel sums its own torque and that of the next 2
     * wheels away from the dynamo, up to a blocked wheel or another block. So a wheel makes power only with a rotation acceptor at most
     * 3 cells along its axis, past unblocked wheels only.
     */
    @Test
    fun `CD-KIN-03 a wheel has demand only with a rotation acceptor within its row of 3 wheels`() {
        val acceptor = RowCell.ROTATION_ACCEPTOR
        val wheel = RowCell.WHEEL
        assertTrue(IeWatermillRule.reachesAcceptor(row(acceptor)), "a dynamo beside the wheel")
        assertTrue(IeWatermillRule.reachesAcceptor(row(wheel, acceptor)))
        assertTrue(IeWatermillRule.reachesAcceptor(row(wheel, wheel, acceptor)), "the third wheel of the row")
        assertFalse(IeWatermillRule.reachesAcceptor(row(wheel, wheel, wheel, acceptor)), "IE sums at most 3 wheels")
        assertFalse(IeWatermillRule.reachesAcceptor(row(RowCell.OTHER, acceptor)), "a blocked wheel or another block ends the row")
        assertFalse(IeWatermillRule.reachesAcceptor(row()), "no dynamo")
        assertFalse(IeWatermillRule.hasDemand(0.0, powered = false), "a dry wheel with no dynamo in its row makes no power")
        assertTrue(IeWatermillRule.hasDemand(0.0, powered = true))
    }

    /** IE computes a wheel's torque only now and then, so a drive that its worker stopped renewing ends at IE's next update. */
    @Test
    fun `CD-KIN-02 a drive holds while its worker renews it, and lapses within 2 ticks of its last renewal`() {
        val drive = WatermillDrive()
        assertFalse(drive.holds(now = 100))
        drive.drive(now = 100, torque = virtual)
        assertEquals(virtual, drive.torque)
        assertTrue(drive.holds(now = 100))
        assertTrue(drive.holds(now = 101))
        drive.renew(now = 101)
        assertTrue(drive.holds(now = 102))
        assertFalse(drive.holds(now = 103), "no renewal on tick 102")
        assertFalse(drive.driven)
        drive.renew(now = 104)
        assertFalse(drive.holds(now = 104), "a renewal does not start a lapsed drive again")
    }

    /** Every end of the claim releases the drive, also after a handler that threw and so skipped its stop. */
    @Test
    fun `CD-KIN-02 a released drive ends at once, and only a driven one asks IE to compute the wheel again`() {
        val drive = WatermillDrive()
        assertFalse(drive.release(), "nothing to release")
        drive.drive(now = 0, torque = virtual)
        assertTrue(drive.release())
        assertFalse(drive.holds(now = 0))
        assertFalse(drive.release(), "a second end of the same claim changes nothing")
    }

    @Test
    fun `CD-KIN-04 the direction saves and loads, and a flip reverses it`() {
        val drive = WatermillDrive()
        assertEquals(1, drive.direction, "a new watermill turns forward")
        drive.flip()
        assertEquals(-1, drive.direction)
        drive.drive(now = 0, torque = virtual)
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
