package codes.honor.cobblemondynamics.leash

import com.mojang.serialization.JsonOps
import io.netty.buffer.Unpooled
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PendingSourceTest {
    private val overworld = ResourceLocation.withDefaultNamespace("overworld")
    private val nether = ResourceLocation.withDefaultNamespace("the_nether")

    @Test
    fun `CD-LSH-19 a pending source lives 599 ticks and expires at 600`() {
        val p = PendingSource(overworld, BlockPos.ZERO, Direction.UP, clickedAt = 1_000)
        assertFalse(p.expired(1_599, 600))
        assertTrue(p.expired(1_600, 600))
    }

    @Test
    fun `CD-SEC-11 a pending source counts only in the level of its click`() {
        val p = PendingSource(overworld, BlockPos(4, 64, -2), Direction.UP, clickedAt = 1_000)
        assertTrue(p.liveIn(overworld, 1_599, 600))
        assertFalse(p.liveIn(nether, 1_000, 600), "a source from the overworld counted for a click in the Nether")
        assertFalse(p.liveIn(overworld, 1_600, 600), "an expired source counted")
    }

    @Test
    fun `the saved and the synced pending source keep the dimension`() {
        val p = PendingSource(nether, BlockPos(-30_000, 5, 12), Direction.NORTH, clickedAt = 123_456_789L)
        val saved = PendingSource.CODEC.encodeStart(JsonOps.INSTANCE, p).getOrThrow()
        assertEquals(p, PendingSource.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow())
        val buffer = Unpooled.buffer()
        PendingSource.STREAM_CODEC.encode(buffer, p)
        assertEquals(p, PendingSource.STREAM_CODEC.decode(buffer))
        assertEquals(0, buffer.readableBytes(), "bytes left after decoding $p")
    }
}
