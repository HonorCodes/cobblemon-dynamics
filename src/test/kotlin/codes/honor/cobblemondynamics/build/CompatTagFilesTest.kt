package codes.honor.cobblemondynamics.build

import codes.honor.cobblemondynamics.role.RoleConditions
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * CD-OPT-12: a built-in tag or data map loads without the mods that its entries name. NeoForge 21.1.265 applies no condition at the
 * top of a tag file or a data map file (neither `TagLoader` nor `DataMapLoader.readData` reads one), so an entry of another mod is
 * optional by itself: `"required": false` in a tag, and its own `neoforge:mod_loaded` condition in a data map, which has no
 * `required` and logs an error for an id that does not exist.
 */
class CompatTagFilesTest {
    private val data = File(System.getProperty("cd.projectDir"), "src/main/resources/data")
    private val own = setOf("minecraft", "c", "cobblemon", "cobblemon_dynamics")

    /** One entry of a file: its block id or `#tag`, and whether it loads without the mod of its namespace. */
    private data class Entry(val id: String, val optional: Boolean)

    private fun namespace(id: String) = id.removePrefix("#").substringBefore(':')

    private fun tagEntries(json: JsonObject): List<Entry> = json.getAsJsonArray("values").map { value ->
        if (value.isJsonPrimitive) {
            Entry(value.asString, optional = false)
        } else {
            val entry = value.asJsonObject
            Entry(entry["id"].asString, optional = entry["required"]?.asBoolean == false)
        }
    }

    private fun dataMapEntries(json: JsonObject): List<Entry> = json.getAsJsonObject("values").entrySet().map { (id, value) ->
        Entry(id, optional = value.isJsonObject && namespace(id) in RoleConditions.mods(value))
    }

    private fun files(kind: String): List<File> =
        data.walk().filter { it.extension == "json" && "/$kind/" in it.invariantSeparatorsPath }.sortedBy { it.path }.toList()

    @Test
    fun `CD-OPT-12 every entry of another mod in a built-in tag or data map is optional`() {
        val tags = files("tags")
        assertTrue(tags.isNotEmpty())
        val all = tags.map { it to tagEntries(JsonParser.parseString(it.readText()).asJsonObject) } +
            files("data_maps").map { it to dataMapEntries(JsonParser.parseString(it.readText()).asJsonObject) }
        for ((file, entries) in all) {
            for (entry in entries) {
                if (namespace(entry.id) in own) continue
                assertTrue(entry.optional, "${file.invariantSeparatorsPath}: ${entry.id} is required")
            }
        }
    }

    @Test
    fun `CD-OPT-12 the check reads an optional tag entry and a data map entry with its mod's condition`() {
        val tag = JsonParser.parseString("""{ "values": ["aether:freezer", { "id": "aether:freezer", "required": false }] }""")
        assertEquals(listOf(Entry("aether:freezer", false), Entry("aether:freezer", true)), tagEntries(tag.asJsonObject))
        val condition = """"neoforge:conditions": [{ "type": "neoforge:mod_loaded", "modid": "twilightforest" }]"""
        val conditional = """"twilightforest:blueberry_bush": { $condition, "value": {} }"""
        val map = JsonParser.parseString("""{ "values": { $conditional, "twilightforest:other": { "value": {} } } }""")
        val expected = listOf(Entry("twilightforest:blueberry_bush", true), Entry("twilightforest:other", false))
        assertEquals(expected, dataMapEntries(map.asJsonObject))
    }
}
