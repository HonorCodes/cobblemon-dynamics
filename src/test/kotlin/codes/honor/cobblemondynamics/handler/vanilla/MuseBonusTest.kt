package codes.honor.cobblemondynamics.handler.vanilla

import codes.honor.cobblemondynamics.role.BuiltInRoles
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class MuseBonusTest {
    private val params: JsonObject by lazy { BuiltInRoles.parse("muse").params }
    private val atLevel100: Double by lazy { MuseBonus.atLevel100(params) }

    /** Easy Magic's `ModEnchantmentMenu.getEnchantingPower` returns the power that its offers read (javap, v21.1.4). */
    @Test
    fun `CD-FIX-05 a power of 10 at Easy Magic's table with a Lv100 Muse reads 15`() {
        val bonus = MuseBonus.bonus(100, params, apothicEnchanting = false).bookshelves
        assertEquals(15, MuseBonus.capped(10, bonus))
    }

    /** Enchanting Infuser's `InfuserMenu.getAvailablePower` clamps to `getEnchantmentPowerLimit()`, 15 for both infusers (javap, v21.1.4). */
    @Test
    fun `CD-FIX-06 an infuser's power rises to its limit and no further, and with Apothic Enchanting the Muse adds nothing`() {
        val bonus = MuseBonus.bonus(100, params, apothicEnchanting = false).bookshelves
        assertEquals(15, MuseBonus.capped(0, bonus, limit = 15))
        assertEquals(12, MuseBonus.capped(0, bonus, limit = 12), "the power passes a lower limit")
        assertEquals(15, MuseBonus.capped(0, bonus, limit = 30), "the Muse adds more than 15 bookshelves")
        assertEquals(20, MuseBonus.capped(20, bonus, limit = 30), "the Muse lowers a power above 15")
        val infuser = ResourceLocation.parse("enchantinginfuser:enchanting_infuser")
        assertFalse(MuseBonus.takesStation(infuser, apothicEnchanting = true), "the Muse works an infuser with Apothic Enchanting")
        assertTrue(MuseBonus.takesStation(infuser, apothicEnchanting = false))
        assertTrue(MuseBonus.takesStation(ResourceLocation.parse("minecraft:enchanting_table"), apothicEnchanting = true))
        assertEquals(0, MuseBonus.bonus(100, params, apothicEnchanting = true).bookshelves)
    }

    /** `InfuserMenu.clickEnchantmentLevelButton` caps a chosen level at the power's `getAvailableEnchantmentLevel` (javap, v21.1.4). */
    @Test
    fun `CD-FIX-06 a chosen level above a new power drops to what it offers, and never below the item's own level`() {
        assertEquals(5, MuseBonus.clampedLevel(chosen = 5, offered = 5, own = 0), "a level within the power changed")
        assertEquals(2, MuseBonus.clampedLevel(chosen = 2, offered = 4, own = 0), "a rise of the power changed the choice")
        assertEquals(3, MuseBonus.clampedLevel(chosen = 5, offered = 3, own = 0))
        assertEquals(0, MuseBonus.clampedLevel(chosen = 3, offered = 0, own = 0), "the item keeps an enchantment that the power lost")
        assertEquals(2, MuseBonus.clampedLevel(chosen = 5, offered = 1, own = 2), "the item lost its own level")
        assertEquals(4, MuseBonus.clampedLevel(chosen = 4, offered = 1, own = 5), "a level that the player lowered changed")
    }

    @Test
    fun `CD-MUS-01 CD-FIX-05 CD-FIX-06 CD-MCH-06 the Muse's tag holds the vanilla table, and the stations of other mods as optional`() {
        val file = File(System.getProperty("cd.projectDir"), "src/main/resources/data/cobblemon_dynamics/tags/block/muse_targets.json")
        val values = JsonParser.parseString(file.readText()).asJsonObject.getAsJsonArray("values")
        val entries = values.associate { value ->
            if (value.isJsonPrimitive) {
                value.asString to true
            } else {
                value.asJsonObject["id"].asString to
                    value.asJsonObject["required"].asBoolean
            }
        }
        val expected = mapOf(
            "minecraft:enchanting_table" to true,
            "easymagic:enchanting_table" to false,
            "enchantinginfuser:enchanting_infuser" to false,
            "enchantinginfuser:advanced_enchanting_infuser" to false,
            "create_enchantment_industry:infuser" to false,
        )
        assertEquals(expected, entries)
    }

    /** CEI's Infuser reads only the stats of Apothic Enchanting, which takes the Muse's Quanta, and it exists only with that mod. */
    @Test
    fun `CD-MCH-06 the Muse takes CEI's Infuser only with Apothic Enchanting, and adds Quanta there`() {
        val infuser = ResourceLocation.parse("create_enchantment_industry:infuser")
        assertTrue(MuseBonus.takesStation(infuser, apothicEnchanting = true))
        assertFalse(MuseBonus.takesStation(infuser, apothicEnchanting = false), "the Muse works an Infuser without its stats")
        assertEquals(MuseBonus.Bonus(0, 45f), MuseBonus.bonus(100, params, apothicEnchanting = true))
    }

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
