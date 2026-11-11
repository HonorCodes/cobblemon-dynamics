package codes.honor.cobblemondynamics.handler.create

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
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

    /** Ruling 2 of the Phase 2 plan: a worker in an up or down current would block it, so only a fan that faces sideways has a spot. */
    @Test
    fun `CD-FAN-03 a catalyst stands in front of a fan that faces sideways, and an up or down fan has no such block`() {
        val fan = BlockPos(3, 64, 7)
        assertEquals(BlockPos(4, 64, 7), FanFront.of(fan, Direction.EAST))
        assertEquals(BlockPos(3, 64, 6), FanFront.of(fan, Direction.NORTH))
        assertNull(FanFront.of(fan, Direction.UP))
        assertNull(FanFront.of(fan, Direction.DOWN))
    }
}
