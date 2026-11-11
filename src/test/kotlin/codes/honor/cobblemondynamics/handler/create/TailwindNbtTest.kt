package codes.honor.cobblemondynamics.handler.create

import net.minecraft.nbt.CompoundTag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class TailwindNbtTest {
    @Test
    fun `CD-TLW-07 the bonus goes only into client packets`() {
        val save = CompoundTag().also { TailwindNbt.write(it, 32, clientPacket = false) }
        assertFalse(save.contains(TailwindNbt.SAILS))
        assertEquals(5, TailwindNbt.sails(save, clientPacket = false, current = 5))
        val packet = CompoundTag().also { TailwindNbt.write(it, 32, clientPacket = true) }
        assertEquals(32, TailwindNbt.sails(packet, clientPacket = true, current = 0))
    }
}
