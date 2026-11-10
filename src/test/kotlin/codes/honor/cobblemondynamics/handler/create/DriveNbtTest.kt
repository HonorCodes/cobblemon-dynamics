package codes.honor.cobblemondynamics.handler.create

import net.minecraft.nbt.CompoundTag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DriveNbtTest {
    @Test
    fun `CD-WHL-04 CD-CRK-05 a target with no saved direction turns at Create's positive speed`() {
        assertEquals(1, DriveNbt.direction(CompoundTag()))
    }

    @Test
    fun `CD-WHL-07 CD-CRK-08 the direction is saved with the target and read back`() {
        for (direction in listOf(-1, 1)) {
            val tag = CompoundTag()
            DriveNbt.write(tag, direction, driven = false, clientPacket = false)
            assertEquals(direction, DriveNbt.direction(tag))
        }
    }

    @Test
    fun `CD-WHL-09 a client packet carries the driven flag, and a save never does`() {
        val packet = CompoundTag()
        DriveNbt.write(packet, 1, driven = true, clientPacket = true)
        assertTrue(DriveNbt.driven(packet, clientPacket = true, current = false))

        // Claims are never saved (CD-CLM-07), so a saved target must never come back driven after a restart.
        val save = CompoundTag()
        DriveNbt.write(save, 1, driven = true, clientPacket = false)
        assertFalse(save.contains(DriveNbt.DRIVEN))
        assertFalse(DriveNbt.driven(save, clientPacket = false, current = false))
    }
}
