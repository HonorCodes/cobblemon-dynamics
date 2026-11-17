package codes.honor.cobblemondynamics.handler.vanilla

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class StokerTagFilesTest {
    private val dir = File(System.getProperty("cd.projectDir"), "src/main/resources/data/cobblemon_dynamics/tags/block")

    @Test
    fun `CD-FIX-01 the Stoker's never tag holds the Aether's Freezer as an optional entry`() {
        val values = JsonParser.parseString(File(dir, "stoker/never.json").readText()).asJsonObject.getAsJsonArray("values")
        val freezer = values.filter { it.isJsonObject }.map { it.asJsonObject }.single { it["id"].asString == "aether:freezer" }
        assertEquals(false, freezer["required"]?.asBoolean, "aether:freezer is required")
    }
}
