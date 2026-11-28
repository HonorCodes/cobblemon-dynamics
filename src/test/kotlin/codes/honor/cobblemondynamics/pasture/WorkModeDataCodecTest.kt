package codes.honor.cobblemondynamics.pasture

import com.mojang.serialization.JsonOps
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WorkModeDataCodecTest {
    @Test
    fun `CD-WMD-02 CD-SHF-02 1_2_2 saved NBT with only enabled loads cleanly with unlocked default and null radius`() {
        val legacy = CompoundTag().apply { putBoolean("enabled", true) }
        val decoded = WorkModeData.CODEC.parse(NbtOps.INSTANCE, legacy).orThrow
        assertEquals(true, decoded.enabled)
        assertEquals(false, decoded.locked)
        assertNull(decoded.radius)
    }

    @Test
    fun `CD-SHF-02 CD-SHF-03 full WorkModeData round-trips enabled locked and optional radius across NBT and JSON`() {
        val samples = listOf(
            WorkModeData(enabled = false, locked = false, radius = null),
            WorkModeData(enabled = true, locked = true, radius = 4),
            WorkModeData(enabled = true, locked = false, radius = 12),
            WorkModeData(enabled = false, locked = true, radius = 16),
        )
        for (sample in samples) {
            val tag = WorkModeData.CODEC.encodeStart(NbtOps.INSTANCE, sample).orThrow
            assertEquals(sample, WorkModeData.CODEC.parse(NbtOps.INSTANCE, tag).orThrow)

            val json = WorkModeData.CODEC.encodeStart(JsonOps.INSTANCE, sample).orThrow
            assertEquals(sample, WorkModeData.CODEC.parse(JsonOps.INSTANCE, json).orThrow)
        }
    }
}
