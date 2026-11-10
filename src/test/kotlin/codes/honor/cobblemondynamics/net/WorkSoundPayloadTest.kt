package codes.honor.cobblemondynamics.net

import io.netty.buffer.Unpooled
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WorkSoundPayloadTest {
    @Test
    fun `the work sound payload survives its codec`() {
        val payload = WorkSoundPayload(42, ResourceLocation.parse("cobblemon:move.flamethrower.actor"))
        val buffer = Unpooled.buffer()
        WorkSoundPayload.STREAM_CODEC.encode(buffer, payload)
        assertEquals(payload, WorkSoundPayload.STREAM_CODEC.decode(buffer))
    }
}
