package codes.honor.cobblemondynamics.bed

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.DataInputStream
import java.io.File

/**
 * The bed content files as the Recipes table and CD-BED-01 state them. The deferred GameTests craft the beds through the real
 * recipe manager and read the block registry.
 */
class BedContentFilesTest {
    private val resources = File(System.getProperty("cd.projectDir"), "src/main/resources")
    private val beds = listOf("hay_bed", "wool_bed", "plush_bed", "starlit_bed")

    private fun json(path: String): JsonObject {
        val file = File(resources, path)
        assertTrue(file.isFile, "missing $path")
        return JsonParser.parseString(file.readText()).asJsonObject
    }

    private fun JsonObject.item(key: String): String = getAsJsonObject("key").getAsJsonObject(key).get("item").asString

    private fun assertResult(recipe: JsonObject, id: String) {
        val result = recipe.getAsJsonObject("result")
        assertEquals("cobblemon_dynamics:$id", result.get("id").asString)
        assertEquals(1, result.get("count").asInt)
        assertEquals("misc", recipe.get("category").asString)
    }

    /**
     * A shaped upgrade: the lower bed is the one center input, so the same grid with air in the center matches no recipe. The
     * corners and the edges take the other two inputs.
     */
    private fun assertUpgrade(id: String, corner: String, edge: String, edgeIsTag: Boolean, lower: String) {
        val recipe = json("data/cobblemon_dynamics/recipe/$id.json")
        assertEquals("minecraft:crafting_shaped", recipe.get("type").asString)
        val pattern = recipe.getAsJsonArray("pattern").map { it.asString }
        val c = pattern[0][0]
        val e = pattern[0][1]
        val b = pattern[1][1]
        assertEquals(listOf("$c$e$c", "$e$b$e", "$c$e$c"), pattern, id)
        assertEquals(3, setOf(c, e, b).size, "$id: the corner, edge and center keys are not distinct")
        val key = recipe.getAsJsonObject("key")
        assertEquals(3, key.size(), "$id: the key has more than the three inputs")
        assertEquals("cobblemon_dynamics:$lower", key.getAsJsonObject("$b").get("item").asString)
        assertEquals("minecraft:$corner", key.getAsJsonObject("$c").get("item").asString)
        assertEquals("minecraft:$edge", key.getAsJsonObject("$e").get(if (edgeIsTag) "tag" else "item").asString)
        assertResult(recipe, id)
    }

    @Test
    fun `CD-BED-02 the Hay Bed is 3 hay bales over 3 planks of any wood`() {
        val recipe = json("data/cobblemon_dynamics/recipe/hay_bed.json")
        assertEquals("minecraft:crafting_shaped", recipe.get("type").asString)
        val pattern = recipe.getAsJsonArray("pattern").map { it.asString }
        assertEquals(2, pattern.size)
        assertEquals(1, pattern[0].toSet().size)
        assertEquals(1, pattern[1].toSet().size)
        assertEquals("minecraft:hay_block", recipe.item(pattern[0][0].toString()))
        val planks = recipe.getAsJsonObject("key").getAsJsonObject(pattern[1][0].toString())
        assertEquals("minecraft:planks", planks.get("tag").asString)
        assertResult(recipe, "hay_bed")
    }

    @Test
    fun `CD-BED-02 the Wool Bed is a Hay Bed with 4 wool blocks of any color and 4 iron ingots`() =
        assertUpgrade("wool_bed", corner = "iron_ingot", edge = "wool", edgeIsTag = true, lower = "hay_bed")

    @Test
    fun `CD-BED-02 the Plush Bed is a Wool Bed with 4 gold ingots and 4 diamonds`() =
        assertUpgrade("plush_bed", corner = "diamond", edge = "gold_ingot", edgeIsTag = false, lower = "wool_bed")

    @Test
    fun `CD-BED-02 the Starlit Bed is a shapeless Plush Bed, Nether Star and 4 Rare Candy`() {
        val recipe = json("data/cobblemon_dynamics/recipe/starlit_bed.json")
        assertEquals("minecraft:crafting_shapeless", recipe.get("type").asString)
        val ingredients = recipe.getAsJsonArray("ingredients").map { it.asJsonObject.get("item").asString }
        val expected = listOf("cobblemon:rare_candy", "cobblemon:rare_candy", "cobblemon:rare_candy", "cobblemon:rare_candy") +
            listOf("cobblemon_dynamics:plush_bed", "minecraft:nether_star")
        assertEquals(expected.sorted(), ingredients.sorted())
        assertResult(recipe, "starlit_bed")
    }

    @Test
    fun `CD-BED-01 each bed drops itself`() {
        for (id in beds) {
            val table = json("data/cobblemon_dynamics/loot_table/blocks/$id.json")
            assertEquals("minecraft:block", table.get("type").asString, id)
            val pool = table.getAsJsonArray("pools").single().asJsonObject
            assertEquals(1, pool.get("rolls").asInt, id)
            val entry = pool.getAsJsonArray("entries").single().asJsonObject
            assertEquals("minecraft:item", entry.get("type").asString, id)
            assertEquals("cobblemon_dynamics:$id", entry.get("name").asString, id)
            val conditions = pool.getAsJsonArray("conditions").map { it.asJsonObject.get("condition").asString }
            assertEquals(listOf("minecraft:survives_explosion"), conditions, id)
        }
    }

    @Test
    fun `CD-BED-01 each bed has its English name`() {
        val lang = json("assets/cobblemon_dynamics/lang/en_us.json")
        val names = listOf("Hay Bed", "Wool Bed", "Plush Bed", "Starlit Bed")
        for ((id, name) in beds.zip(names)) assertEquals(name, lang.get("block.cobblemon_dynamics.$id")?.asString, id)
    }

    /** The template is the bed's shape, 6/16 high, so the model matches the block's collision box. */
    @Test
    fun `CD-BED-01 each bed's model is the low template with its own top and side`() {
        val template = json("assets/cobblemon_dynamics/models/block/template_bed.json")
        val element = template.getAsJsonArray("elements").single().asJsonObject
        assertEquals(listOf(0.0, 0.0, 0.0), element.getAsJsonArray("from").map { it.asDouble })
        assertEquals(listOf(16.0, 16.0 * BedBlock.HEIGHT, 16.0), element.getAsJsonArray("to").map { it.asDouble })
        for (id in beds) {
            val variant = json("assets/cobblemon_dynamics/blockstates/$id.json").getAsJsonObject("variants").getAsJsonObject("")
            assertEquals("cobblemon_dynamics:block/$id", variant.get("model").asString, id)
            val model = json("assets/cobblemon_dynamics/models/block/$id.json")
            assertEquals("cobblemon_dynamics:block/template_bed", model.get("parent").asString, id)
            val textures = model.getAsJsonObject("textures")
            for (face in listOf("top", "side")) {
                assertEquals("cobblemon_dynamics:block/${id}_$face", textures.get(face).asString, "$id $face")
            }
            val item = json("assets/cobblemon_dynamics/models/item/$id.json")
            assertEquals("cobblemon_dynamics:block/$id", item.get("parent").asString, id)
        }
    }

    /** CD-ART-11: Phase 1 ships placeholder textures, each an original 16 × 16 PNG. */
    @Test
    fun `CD-ART-11 each bed texture is a 16 x 16 PNG`() {
        for (id in beds) {
            for (face in listOf("top", "side")) {
                val file = File(resources, "assets/cobblemon_dynamics/textures/block/${id}_$face.png")
                assertTrue(file.isFile, "missing ${file.name}")
                DataInputStream(file.inputStream()).use { png ->
                    val signature = ByteArray(8).also(png::readFully)
                    assertEquals(listOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A), signature.map { it.toInt() and 0xFF }, file.name)
                    png.readInt()
                    assertEquals("IHDR", String(ByteArray(4).also(png::readFully)), file.name)
                    assertEquals(16 to 16, png.readInt() to png.readInt(), file.name)
                }
            }
        }
    }
}
