package codes.honor.cobblemondynamics.handler.create

import net.minecraft.nbt.CompoundTag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FanNbtTest {
    @Test
    fun `CD-FAN-09 a fan with no saved mode blasts`() = assertEquals(FireMode.BLASTING, FanNbt.mode(CompoundTag()))

    @Test
    fun `CD-FAN-11 a fan's mode survives a save`() {
        val tag = CompoundTag().also { FanNbt.write(it, FireMode.SMOKING, FanCatalyst.FIRE, clientPacket = false) }
        assertEquals(FireMode.SMOKING, FanNbt.mode(tag))
    }

    @Test
    fun `CD-FAN-14 the client packet carries the catalyst and the mode, and a save never holds the catalyst`() {
        val save = CompoundTag().also { FanNbt.write(it, FireMode.BLASTING, FanCatalyst.GHOST, clientPacket = false) }
        assertFalse(save.contains(FanNbt.CATALYST))
        assertEquals(FanCatalyst.WATER, FanNbt.catalyst(save, clientPacket = false, current = FanCatalyst.WATER))
        val packet = CompoundTag().also { FanNbt.write(it, FireMode.SMOKING, FanCatalyst.GHOST, clientPacket = true) }
        assertEquals(FanCatalyst.GHOST, FanNbt.catalyst(packet, clientPacket = true, current = null))
        assertEquals(FireMode.SMOKING, FanNbt.mode(packet))
        val cleared = CompoundTag().also { FanNbt.write(it, FireMode.BLASTING, null, clientPacket = true) }
        assertNull(FanNbt.catalyst(cleared, clientPacket = true, current = FanCatalyst.FIRE))
    }

    @Test
    fun `CD-FAN-10 a flip switches between blasting and smoking`() {
        assertEquals(FireMode.SMOKING, FireMode.BLASTING.flipped())
        assertEquals(FireMode.BLASTING, FireMode.SMOKING.flipped())
    }
}
