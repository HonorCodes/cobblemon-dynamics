package codes.honor.cobblemondynamics.handler.vanilla

import codes.honor.cobblemondynamics.role.BuiltInRoles
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MuseBonusTest {
    private val params: JsonObject by lazy { BuiltInRoles.parse("muse").params }
    private val atLevel100: Double by lazy { MuseBonus.atLevel100(params) }

    @Test
    fun `CD-MUS-03 the default Muse adds 1, 2, 8 and 15 bookshelves at levels 1, 7, 50 and 100`() {
        assertEquals(listOf(1, 2, 8, 15), listOf(1, 7, 50, 100).map { MuseBonus.forLevel(it, atLevel100) })
    }

    @Test
    fun `CD-MUS-04 with a Lv50 Muse, 12, 7 and 6 real bookshelves count as 15, 15 and 14`() {
        val bonus = MuseBonus.forLevel(50, atLevel100)
        assertEquals(listOf(15, 15, 14), listOf(12, 7, 6).map { MuseBonus.capped(it, bonus) })
    }

    @Test
    fun `CD-MUS-04 with no Muse the count stays as vanilla passes it, and a Muse never lowers it`() {
        assertEquals(6, MuseBonus.capped(6, 0))
        assertEquals(20, MuseBonus.capped(20, 0))
        assertEquals(20, MuseBonus.capped(20, 8))
        assertEquals(15, MuseBonus.capped(14, 8))
        assertEquals(15, MuseBonus.capped(15, 8))
    }

    @Test
    fun `CD-MUS-03 a role file without the formula takes the default, and a pack's formula applies up to the cap`() {
        assertEquals(15.0, MuseBonus.atLevel100(JsonObject()))
        assertEquals(15.0, MuseBonus.atLevel100(JsonParser.parseString("""{ "bookshelves_at_level_100": "many" }""").asJsonObject))
        val thirty = MuseBonus.atLevel100(JsonParser.parseString("""{ "bookshelves_at_level_100": 30 }""").asJsonObject)
        assertEquals(15, MuseBonus.forLevel(50, thirty))
        assertEquals(15, MuseBonus.forLevel(100, thirty))
        assertEquals(0, MuseBonus.forLevel(50, -3.0))
    }

    @Test
    fun `CD-MUS-07 the default Muse adds 1, 5, 12, 23, 34 and 45 Quanta at levels 1, 10, 25, 50, 75 and 100`() {
        val quanta = MuseBonus.quantaAtLevel100(params)
        assertEquals(listOf(1f, 5f, 12f, 23f, 34f, 45f), listOf(1, 10, 25, 50, 75, 100).map { MuseBonus.quantaForLevel(it, quanta) })
    }

    @Test
    fun `CD-MUS-07 with Apothic Enchanting a Muse adds Quanta and no bookshelves, and without it bookshelves and no Quanta`() {
        assertEquals(MuseBonus.Bonus(bookshelves = 0, quanta = 45f), MuseBonus.bonus(100, params, apothicEnchanting = true))
        assertEquals(MuseBonus.Bonus(bookshelves = 0, quanta = 23f), MuseBonus.bonus(50, params, apothicEnchanting = true))
        assertEquals(MuseBonus.Bonus(bookshelves = 15, quanta = 0f), MuseBonus.bonus(100, params, apothicEnchanting = false))
        assertEquals(MuseBonus.Bonus(bookshelves = 8, quanta = 0f), MuseBonus.bonus(50, params, apothicEnchanting = false))
    }

    @Test
    fun `CD-MUS-07 a role file without the Quanta formula takes 45, and a pack's formula applies up to Apothic's cap of 100`() {
        assertEquals(45.0, MuseBonus.quantaAtLevel100(JsonObject()))
        assertEquals(45.0, MuseBonus.quantaAtLevel100(JsonParser.parseString("""{ "quanta_at_level_100": "lots" }""").asJsonObject))
        val shelvesOnly = JsonParser.parseString("""{ "bookshelves_at_level_100": 30 }""").asJsonObject
        assertEquals(MuseBonus.Bonus(bookshelves = 0, quanta = 45f), MuseBonus.bonus(100, shelvesOnly, apothicEnchanting = true))
        val strong = MuseBonus.quantaAtLevel100(JsonParser.parseString("""{ "quanta_at_level_100": 300 }""").asJsonObject)
        assertEquals(listOf(3f, 100f, 100f), listOf(1, 50, 100).map { MuseBonus.quantaForLevel(it, strong) })
        assertEquals(0f, MuseBonus.quantaForLevel(50, -10.0))
        assertEquals(0f, MuseBonus.quantaForLevel(-5, 45.0))
        assertEquals(0f, MuseBonus.quantaForLevel(50, Double.NaN))
    }
}
