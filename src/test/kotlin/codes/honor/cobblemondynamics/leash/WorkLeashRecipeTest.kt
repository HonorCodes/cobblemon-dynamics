package codes.honor.cobblemondynamics.leash

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class WorkLeashRecipeTest {
    private val file = File(System.getProperty("cd.projectDir"), "src/main/resources/data/cobblemon_dynamics/recipe/work_leash.json")

    /** The recipe file as the Recipes table states it. The deferred GameTest crafts it through the real recipe manager. */
    @Test
    fun `CD-LSH-15 the Work Leash recipe is a shapeless Lead and Poke Ball`() {
        val json = JsonParser.parseString(file.readText()).asJsonObject
        assertEquals("minecraft:crafting_shapeless", json.get("type").asString)
        val ingredients = json.getAsJsonArray("ingredients").map { it.asJsonObject.get("item").asString }
        assertEquals(listOf("cobblemon:poke_ball", "minecraft:lead"), ingredients.sorted())
        val result = json.getAsJsonObject("result")
        assertEquals("cobblemon_dynamics:work_leash", result.get("id").asString)
        assertEquals(1, result.get("count").asInt)
    }
}
