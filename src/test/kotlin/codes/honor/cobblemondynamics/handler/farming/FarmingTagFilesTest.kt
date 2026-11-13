package codes.honor.cobblemondynamics.handler.farming

import codes.honor.cobblemondynamics.fx.CobblemonJar
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/** The default tags of the farming roles against the adapter table of ADR-0015 and the tag files of the Cobblemon jar. */
class FarmingTagFilesTest {
    private val dir = File(System.getProperty("cd.projectDir"), "src/main/resources/data/cobblemon_dynamics/tags/block")

    /** One value of a tag file: a block id or a `#tag`, and whether the tag needs it to load. */
    private data class Entry(val id: String, val required: Boolean)

    private fun entries(json: String): List<Entry> = JsonParser.parseString(json).asJsonObject.getAsJsonArray("values").map { value ->
        if (value.isJsonPrimitive) {
            Entry(value.asString, required = true)
        } else {
            val entry = value.asJsonObject
            Entry(entry["id"].asString, entry["required"]?.asBoolean ?: true)
        }
    }

    private val ownFiles = listOf(
        "cultivator_targets",
        "harvester_targets",
        "harvest/never",
        "harvest/pick_to_age_0",
        "harvest/pick_to_age_1",
        "harvest/upper_half",
        "harvest/break_and_replant",
    )

    private fun own(path: String): List<Entry> = entries(File(dir, "$path.json").readText())

    /** The values of [tag]: the mod's own files, Cobblemon's from its jar, and `#minecraft:crops` as RES8 read it. */
    private fun values(tag: String): List<Entry> {
        val namespace = tag.substringBefore(':')
        val path = tag.substringAfter(':')
        return when {
            namespace == "cobblemon_dynamics" -> own(path)
            namespace == "cobblemon" -> entries(Files.readString(CobblemonJar.root.resolve("data/cobblemon/tags/block/$path.json")))
            tag == "minecraft:crops" -> MINECRAFT_CROPS.map { Entry(it, required = true) }
            else -> error("no tag file for #$tag")
        }
    }

    /** Every block id of [tag], through its nested tags. */
    private fun resolve(tag: String): Set<String> =
        values(tag).flatMapTo(HashSet()) { if (it.id.startsWith("#")) resolve(it.id.drop(1)) else setOf(it.id) }

    @Test
    fun `CD-CRP-01 CD-CRP-02 each family of the adapter table is in its tags`() {
        val cultivator = resolve("cobblemon_dynamics:cultivator_targets")
        for (block in GROWN) assertTrue(block in cultivator, "the Cultivator's tag lacks $block")
        val harvester = resolve("cobblemon_dynamics:harvester_targets")
        for (block in HARVESTED) assertTrue(block in harvester, "the Harvester's tag lacks $block")
        for ((path, blocks) in ADAPTER_TAGS) {
            val tagged = resolve("cobblemon_dynamics:$path")
            for (block in blocks) assertTrue(block in tagged, "#cobblemon_dynamics:$path lacks $block")
        }
        assertTrue("#minecraft:crops" in own("cultivator_targets").map { it.id })
        assertTrue("#minecraft:crops" in own("harvester_targets").map { it.id })
    }

    @Test
    fun `CD-CRP-01 CD-CRP-02 every entry of another mod is optional`() {
        for (path in ownFiles) {
            for (entry in own(path)) {
                val namespace = entry.id.removePrefix("#").substringBefore(':')
                if (namespace !in setOf("minecraft", "cobblemon", "cobblemon_dynamics")) {
                    assertFalse(entry.required, "${entry.id} in $path.json is required")
                }
                assertFalse(entry.id == "#minecraft:bee_growables", "$path.json names #minecraft:bee_growables")
                assertFalse(entry.id == "#cobblemon:crops", "$path.json names #cobblemon:crops, which holds the terrain seeds")
            }
        }
    }

    @Test
    fun `CD-CUL-10 the Cultivator's tag holds no apricorn and no terrain seed`() {
        val excluded = resolve("cobblemon:apricorns") + resolve("cobblemon:terrain_seeds")
        assertEquals(11, excluded.size, "the Cobblemon jar changed its apricorns or terrain seeds")
        assertEquals(emptySet<String>(), resolve("cobblemon_dynamics:cultivator_targets").intersect(excluded))
        assertEquals(emptySet<String>(), resolve("cobblemon_dynamics:harvester_targets").intersect(excluded))
        assertTrue(resolve("cobblemon:apricorns").all { it in resolve("cobblemon_dynamics:harvest/never") })
    }

    private companion object {
        /** The members of `#minecraft:crops` that RES8 read: vanilla's, and those of COHO 2.5.0's crop mods, with one Pam's crop of 97. */
        val MINECRAFT_CROPS = setOf(
            "minecraft:wheat", "minecraft:carrots", "minecraft:potatoes", "minecraft:beetroots", "minecraft:melon_stem",
            "minecraft:pumpkin_stem", "minecraft:torchflower_crop", "minecraft:pitcher_crop",
            "farmersdelight:cabbages", "farmersdelight:onions", "farmersdelight:rice_panicles", "farmersdelight:budding_tomatoes",
            "farmersdelight:tomatoes", "farmersdelight:tomatoes_on_rope",
            "expandeddelight:asparagus_crop", "expandeddelight:sweet_potato_crop", "expandeddelight:chili_pepper_crop",
            "expandeddelight:peanut_crop", "minersdelight:cave_carrots", "supplementaries:flax", "supplementaries:rope_tomatoes",
            "supplementaries:stick_tomatoes", "immersiveengineering:hemp", "biomeswevegone:oddion_crop",
            "biomeswevegone:pale_pumpkin_stem", "deep_aether:squash_stem", "undergarden:gloomgourd_stem", "ars_nouveau:magebloom_crop",
            "occultism:datura", "pamhc2crops:pamtomatocrop",
        )

        /** One block of each family of the adapter table that the growth adapter takes. */
        val GROWN = listOf(
            "minecraft:wheat", "minecraft:torchflower_crop", "minecraft:melon_stem", "minecraft:sweet_berry_bush",
            "minecraft:cave_vines", "minecraft:cave_vines_plant", "minecraft:cocoa", "cobblemon:cheri_berry", "cobblemon:red_mint",
            "cobblemon:revival_herb", "cobblemon:medicinal_leek", "cobblemon:vivichoke_seeds", "cobblemon:hearty_grains",
            "cobblemon:galarica_nut_bush", "farmersdelight:cabbages", "farmersdelight:tomatoes", "farmersdelight:budding_tomatoes",
            "farmersdelight:rice", "farmersdelight:rice_panicles", "expandeddelight:asparagus_crop", "expandeddelight:cranberry_plant",
            "minersdelight:cave_carrots", "supplementaries:flax", "supplementaries:rope_tomatoes", "immersiveengineering:hemp",
            "aether:berry_bush_stem", "deep_aether:squash_stem", "biomeswevegone:pale_pumpkin_stem", "biomeswevegone:blueberry_bush",
            "biomeswevegone:oddion_crop", "undergarden:underbean_bush", "undergarden:blisterberry_bush", "undergarden:gloomgourd_stem",
            "ars_nouveau:magebloom_crop", "ars_nouveau:sourceberry_bush", "occultism:datura", "regions_unexplored:salmonberry_bush",
            "regions_unexplored:duskmelon", "pamhc2crops:pamtomatocrop", "pamhc2trees:pamapple", "pamhc2trees:pammaple",
        )

        /** One block of each family of the adapter table that a harvest adapter takes. */
        val HARVESTED = listOf(
            "minecraft:wheat", "minecraft:attached_melon_stem", "minecraft:attached_pumpkin_stem", "minecraft:sweet_berry_bush",
            "minecraft:cave_vines", "minecraft:cave_vines_plant", "minecraft:cocoa", "minecraft:nether_wart", "cobblemon:cheri_berry",
            "cobblemon:red_mint", "cobblemon:revival_herb", "cobblemon:medicinal_leek", "cobblemon:vivichoke_seeds",
            "cobblemon:hearty_grains", "cobblemon:galarica_nut_bush", "farmersdelight:cabbages", "farmersdelight:tomatoes",
            "farmersdelight:tomatoes_on_rope", "farmersdelight:rice_panicles", "expandeddelight:asparagus_crop",
            "expandeddelight:cranberry_plant", "minersdelight:cave_carrots", "supplementaries:flax", "immersiveengineering:hemp",
            "aether:berry_bush", "deep_aether:attached_squash_stem", "biomeswevegone:attached_pale_pumpkin_stem",
            "biomeswevegone:blueberry_bush", "biomeswevegone:oddion_crop", "undergarden:underbean_bush", "undergarden:blisterberry_bush",
            "undergarden:gloomgourd_stem_attached", "ars_nouveau:magebloom_crop", "ars_nouveau:sourceberry_bush", "occultism:datura",
            "regions_unexplored:salmonberry_bush", "regions_unexplored:duskmelon", "pamhc2crops:pamtomatocrop", "pamhc2trees:pamapple",
            "pamhc2trees:pammaple",
        )

        /** The families whose harvest adapter is a tag, by the tag's path. */
        val ADAPTER_TAGS = mapOf(
            "harvest/never" to listOf(
                "minecraft:melon_stem", "minecraft:pumpkin_stem", "minecraft:torchflower_crop", "minecraft:pitcher_crop",
                "cobblemon:red_apricorn", "farmersdelight:budding_tomatoes", "supplementaries:rope_tomatoes",
                "supplementaries:stick_tomatoes", "deep_aether:squash_stem", "biomeswevegone:pale_pumpkin_stem",
                "undergarden:gloomgourd_stem",
            ),
            "harvest/pick_to_age_0" to listOf(
                "farmersdelight:tomatoes",
                "farmersdelight:tomatoes_on_rope",
                "regions_unexplored:duskmelon",
                "pamhc2trees:pamapple",
                "pamhc2trees:pammaple",
            ),
            "harvest/pick_to_age_1" to listOf(
                "minecraft:sweet_berry_bush", "cobblemon:galarica_nut_bush", "expandeddelight:cranberry_plant",
                "biomeswevegone:blueberry_bush", "biomeswevegone:oddion_crop", "undergarden:underbean_bush",
                "undergarden:blisterberry_bush", "ars_nouveau:sourceberry_bush", "regions_unexplored:salmonberry_bush",
            ),
            "harvest/upper_half" to listOf("cobblemon:hearty_grains", "immersiveengineering:hemp"),
            "harvest/break_and_replant" to listOf("minecraft:nether_wart", "minecraft:cocoa", "aether:berry_bush"),
        )
    }
}
