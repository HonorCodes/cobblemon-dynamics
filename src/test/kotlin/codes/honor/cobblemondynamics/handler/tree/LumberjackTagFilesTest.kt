package codes.honor.cobblemondynamics.handler.tree

import codes.honor.cobblemondynamics.fx.CobblemonJar
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/** The default tags of the Lumberjack (ADR-0017, decisions 2, 3 and 10). */
class LumberjackTagFilesTest {
    private val dir = File(System.getProperty("cd.projectDir"), "src/main/resources/data/cobblemon_dynamics/tags/block")

    /** One value of a tag file: a block id or a `#tag`, and whether the tag needs it to load. */
    private data class Entry(val id: String, val required: Boolean)

    private fun entries(path: String): List<Entry> {
        val json = JsonParser.parseString(File(dir, "$path.json").readText()).asJsonObject
        assertEquals(false, json["replace"]?.asBoolean, "$path.json must not replace the tags of other packs")
        return json.getAsJsonArray("values").map { value ->
            if (value.isJsonPrimitive) {
                Entry(value.asString, required = true)
            } else {
                Entry(value.asJsonObject["id"].asString, value.asJsonObject["required"]?.asBoolean ?: true)
            }
        }
    }

    @Test
    fun `CD-LUM-02 the default target tag holds the logs tag of Minecraft`() {
        assertEquals(listOf(Entry("#minecraft:logs", required = true)), entries("lumberjack_targets"))
    }

    @Test
    fun `CD-LUM-08 the never tag holds each default, and each entry of another mod is optional`() {
        val never = entries("lumberjack/never")
        assertEquals(NEVER.toSet(), never.map { it.id }.toSet())
        assertEquals(never.size, never.map { it.id }.toSet().size, "an entry is listed twice")
        for (entry in never) {
            val namespace = entry.id.removePrefix("#").substringBefore(':')
            val ours = namespace in setOf("minecraft", "cobblemon", "cobblemon_dynamics")
            assertEquals(ours, entry.required, "${entry.id} is required: ${entry.required}")
        }
    }

    /** Cobblemon is a required dependency, so its entries are required, and each must exist in the jar that the build pins. */
    @Test
    fun `CD-LUM-08 each Cobblemon entry of the never tag is in the Cobblemon jar`() {
        for (entry in entries("lumberjack/never").map { it.id }.filter { it.removePrefix("#").startsWith("cobblemon:") }) {
            val path = entry.removePrefix("#").substringAfter(':')
            val file = if (entry.startsWith("#")) "data/cobblemon/tags/block/$path.json" else "assets/cobblemon/blockstates/$path.json"
            assertTrue(Files.exists(CobblemonJar.root.resolve(file)), "the Cobblemon jar has no $file for $entry")
        }
        assertTrue(File(dir, "harvest/pick_to_age_0.json").exists(), "the never tag names a farming tag that does not exist")
    }

    @Test
    fun `CD-LUM-18 the needs_four tag holds the dark oak sapling`() {
        assertEquals(listOf(Entry("minecraft:dark_oak_sapling", required = true)), entries("lumberjack/needs_four"))
    }

    private companion object {
        /** ADR-0017, decision 3. */
        val NEVER = listOf(
            "#cobblemon_dynamics:harvest/pick_to_age_0",
            "#cobblemon:apricorns",
            "#cobblemon:apricorn_logs",
            "cobblemon:apricorn_leaves",
            "twilightforest:time_log_core",
            "twilightforest:transformation_log_core",
            "twilightforest:mining_log_core",
            "twilightforest:sorting_log_core",
            "minecraft:bee_nest",
            "minecraft:beehive",
            "#minecraft:crimson_stems",
            "#minecraft:warped_stems",
            "#minecraft:mangrove_logs",
            "unusualend:chorus_cane_block",
            "betterarcheology:rotten_log",
        )
    }

    /** R82: the saplings whose grown trees the never tag always skips. */
    @Test
    fun `CD-LUM-20 the never-sapling tag holds its defaults, and each entry of another mod is optional`() {
        val entries = entries("lumberjack/never_saplings")
        val ids = entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "an entry is listed twice")
        val magic = listOf("time", "transformation", "mining", "sorting").map { "twilightforest:${it}_sapling" }
        val missing = listOf("minecraft:mangrove_propagule", "#cobblemon:apricorn_saplings") + magic - ids.toSet()
        assertTrue(missing.isEmpty(), "the never-sapling tag lacks $missing")
        assertEquals(50, ids.count { it.startsWith("pamhc2trees:") && it.endsWith("_sapling") }, "the saplings of Pam's trees")
        for (entry in entries) {
            val namespace = entry.id.removePrefix("#").substringBefore(':')
            assertEquals(namespace in setOf("minecraft", "cobblemon"), entry.required, "${entry.id} is required: ${entry.required}")
        }
        assertTrue(Files.exists(CobblemonJar.root.resolve("data/cobblemon/tags/block/apricorn_saplings.json")))
    }
}
