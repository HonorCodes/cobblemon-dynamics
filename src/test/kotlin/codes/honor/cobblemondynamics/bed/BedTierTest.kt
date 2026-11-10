package codes.honor.cobblemondynamics.bed

import codes.honor.cobblemondynamics.config.EngineSettings
import codes.honor.cobblemondynamics.stamina.StaminaMath
import com.google.gson.JsonPrimitive
import com.mojang.serialization.JsonOps
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BedTierTest {
    @Test
    fun `CD-BED-01 four tiers 1 to 4 with ids hay_bed, wool_bed, plush_bed and starlit_bed`() {
        assertEquals(listOf(1, 2, 3, 4), BedTier.entries.map { it.tier })
        assertEquals(listOf("hay_bed", "wool_bed", "plush_bed", "starlit_bed"), BedTier.entries.map { it.id })
        for (bed in BedTier.entries) assertEquals(bed, BedTier.of(bed.tier))
        assertNull(BedTier.of(0))
        assertNull(BedTier.of(5))
    }

    @Test
    fun `CD-BED-01 CD-BED-03 the tiers take the factors 2, 3, 4 and 8`() =
        assertEquals(listOf(2, 3, 4, 8), BedTier.entries.map { StaminaMath.bedFactor(it.tier, EngineSettings.DEFAULT) })

    @Test
    fun `CD-BED-01 the block codec names each tier`() {
        assertEquals(listOf("hay", "wool", "plush", "starlit"), BedTier.entries.map { it.serializedName })
        for (bed in BedTier.entries) {
            val json = BedTier.CODEC.encodeStart(JsonOps.INSTANCE, bed).getOrThrow()
            assertEquals(JsonPrimitive(bed.serializedName), json)
            assertEquals(bed, BedTier.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow())
        }
    }
}
