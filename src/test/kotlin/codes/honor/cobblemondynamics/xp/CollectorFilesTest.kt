package codes.honor.cobblemondynamics.xp

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** The data files of the Experience Collector, read from the source tree as a data pack load reads them. */
class CollectorFilesTest {
    private val resources = File(System.getProperty("cd.projectDir"), "src/main/resources")

    private fun json(path: String): JsonObject = JsonParser.parseString(File(resources, path).readText()).asJsonObject

    @Test
    fun `CD-XPC-16 the block is in the tag minecraft mineable pickaxe`() {
        val values = json("data/minecraft/tags/block/mineable/pickaxe.json").getAsJsonArray("values").map { it.asString }
        assertTrue(COLLECTOR in values, "mineable/pickaxe holds $values")
    }

    /**
     * The grid of the Recipes table: iron ingots in the 4 corners, glass in the top middle and on both sides of the middle row, a block
     * of lapis lazuli in the centre, and a hopper in the bottom middle. The GameTest of CD-XPC-14 is deferred, so this reads the grid.
     */
    @Test
    fun `CD-XPC-14 the shaped recipe makes one collector from the grid of the Recipes table`() {
        val recipe = json("data/cobblemon_dynamics/recipe/experience_collector.json")
        assertEquals("minecraft:crafting_shaped", recipe.get("type").asString)
        val key = recipe.getAsJsonObject("key").entrySet().associate { (symbol, ingredient) ->
            symbol.single() to ingredient.asJsonObject.get("item").asString
        }
        val grid = recipe.getAsJsonArray("pattern").map { row -> row.asString.map { key.getValue(it) } }
        val iron = "minecraft:iron_ingot"
        val glass = "minecraft:glass"
        val expected = listOf(
            listOf(iron, glass, iron),
            listOf(glass, "minecraft:lapis_block", glass),
            listOf(iron, "minecraft:hopper", iron),
        )
        assertEquals(expected, grid)
        val result = recipe.getAsJsonObject("result")
        assertEquals(COLLECTOR, result.get("id").asString)
        assertEquals(1, result.get("count").asInt)
    }

    /** The drop of a broken collector copies the XP component from the block entity, as the Charge Cell's drop copies its FE. */
    @Test
    fun `CD-XPC-13 the loot table copies the stored XP to the drop, and the tooltip reads 12,345 of 100,000 XP`() {
        val entry = json("data/cobblemon_dynamics/loot_table/blocks/experience_collector.json")
            .getAsJsonArray("pools").single().asJsonObject.getAsJsonArray("entries").single().asJsonObject
        assertEquals(COLLECTOR, entry.get("name").asString)
        val functions = entry.getAsJsonArray("functions").map { it.asJsonObject }
        val copy = functions.single { it.get("function").asString == "minecraft:copy_components" }
        assertEquals("block_entity", copy.get("source").asString)
        assertEquals(listOf("cobblemon_dynamics:stored_experience"), copy.getAsJsonArray("include").map { it.asString })
        val lang = json("assets/cobblemon_dynamics/lang/en_us.json")
        val tooltip = lang.get(ExperienceCollectorBlock.STORED_KEY).asString
        assertEquals("12,345 / 100,000 XP", String.format(tooltip, CollectorStore.format(12_345), CollectorStore.format(100_000)))
    }

    /**
     * An explosion with drop decay, as a creeper's, rolls `minecraft:survives_explosion` at 1 in its radius, so a condition anywhere in
     * these tables loses the XP or the FE of most exploded blocks (R86). The GameTests of CD-XPC-13 and CD-GEN-11 explode real blocks.
     */
    @Test
    fun `CD-XPC-13 CD-GEN-11 the collector and the Charge Cell drop themselves whatever breaks them`() {
        for (block in listOf("experience_collector", "charge_cell")) {
            val table = json("data/cobblemon_dynamics/loot_table/blocks/$block.json")
            val pool = table.getAsJsonArray("pools").single().asJsonObject
            val entry = pool.getAsJsonArray("entries").single().asJsonObject
            assertEquals("cobblemon_dynamics:$block", entry.get("name").asString)
            for (part in listOf(table, pool, entry)) assertNull(part.get("conditions"), "$block: a loot condition can drop nothing: $part")
        }
    }

    private companion object {
        const val COLLECTOR = "cobblemon_dynamics:experience_collector"
    }
}
