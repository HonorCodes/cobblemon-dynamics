package codes.honor.cobblemondynamics.handler.vanilla

import codes.honor.cobblemondynamics.role.BuiltInRoles
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MuseBonusTest {
    private val atLevel100: Double by lazy { MuseBonus.atLevel100(BuiltInRoles.parse("muse").params) }

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
}
