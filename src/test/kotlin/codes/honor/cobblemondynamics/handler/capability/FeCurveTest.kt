package codes.honor.cobblemondynamics.handler.capability

import codes.honor.cobblemondynamics.role.BuiltInRoles
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FeCurveTest {
    private fun fe(l: Int, rare: Boolean = false) = FeCurve.fePerTick(l, rare, 10, 5_000, 2.0)

    private val defaults: GeneratorParams by lazy { GeneratorParams.of(BuiltInRoles.parse("generator").params) }

    private fun params(max: Int): GeneratorParams = GeneratorParams.of(
        JsonParser.parseString(
            """{ "base_fe_per_tick": 10, "max_fe_per_tick": $max, "rare_multiplier": 2.0, "rare_labels": ["legendary", "mythical"] }""",
        ).asJsonObject,
    )

    @Test
    fun `CD-GEN-02 the curve hits 10, 45, 217, 1041 and 5000`() =
        assertEquals(listOf(10, 45, 217, 1_041, 5_000), listOf(1, 25, 50, 75, 100).map { fe(it) })

    @Test
    fun `CD-GEN-02 legendary doubles before rounding`() {
        assertEquals(433, fe(50, true))
        assertEquals(10_000, fe(100, true))
    }

    @Test
    fun `CD-GEN-03 ultra_beast and paradox are not rare`() {
        assertFalse(FeCurve.isRare(setOf("ultra_beast"), setOf("legendary", "mythical")))
        assertFalse(FeCurve.isRare(setOf("paradox"), setOf("legendary", "mythical")))
        assertTrue(FeCurve.isRare(setOf("mythical"), setOf("legendary", "mythical")))
    }

    /** Cobblemon compares labels without case (`Pokemon.hasLabels`). */
    @Test
    fun `CD-GEN-03 labels match without case`() {
        assertTrue(FeCurve.isRare(setOf("Legendary"), setOf("legendary")))
        assertTrue(FeCurve.isRare(setOf("mythical"), setOf("MYTHICAL")))
    }

    @Test
    fun `CD-GEN-03 a Lv100 ultra beast and a Lv100 paradox Pokemon each give 5,000 FE per tick with the default file`() {
        assertEquals(5_000, defaults.fePerTick(100, setOf("ultra_beast", "gen7")))
        assertEquals(5_000, defaults.fePerTick(100, setOf("paradox", "gen9")))
        assertEquals(10_000, defaults.fePerTick(100, setOf("legendary", "gen1")))
        assertEquals(433, defaults.fePerTick(50, setOf("mythical")))
    }

    @Test
    fun `CD-CFG-03 the default Generator file holds 10, 5,000, 2 and the labels legendary and mythical`() =
        assertEquals(GeneratorParams(10, 5_000, 2.0, setOf("legendary", "mythical")), defaults)

    @Test
    fun `CD-CFG-03 a level-100 rate of 2,500 gives a Lv100 Generator 2,500 FE per tick`() =
        assertEquals(2_500, params(max = 2_500).fePerTick(100, emptySet()))

    @Test
    fun `CD-CFG-13 a level-100 rate of 20,000 gives a Lv100 legendary 40,000 FE per tick`() =
        assertEquals(40_000, params(max = 20_000).fePerTick(100, setOf("legendary")))

    /** The level-100 rate stays the most a worker gives when a server raises Cobblemon's level cap (CD-BAL-01). */
    @Test
    fun `a level outside 1 to 100 takes the rate of the nearest end of the curve`() {
        assertEquals(10, fe(0))
        assertEquals(5_000, fe(150))
        assertEquals(10_000, fe(1_000, true))
    }

    @Test
    fun `a rate too large for an int offers the largest int`() =
        assertEquals(Int.MAX_VALUE, FeCurve.fePerTick(100, true, 10, Int.MAX_VALUE, 2.0))

    @Test
    fun `a file with no params gives the default file's numbers`() = assertEquals(defaults, GeneratorParams.of(JsonObject()))
}
