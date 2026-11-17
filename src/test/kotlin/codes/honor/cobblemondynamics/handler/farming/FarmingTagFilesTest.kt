package codes.honor.cobblemondynamics.handler.farming

import codes.honor.cobblemondynamics.fx.CobblemonJar
import codes.honor.cobblemondynamics.role.RoleConditions
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
        "cultivator/never",
        "harvester_targets",
        "harvest/never",
        "harvest/pick_to_age_0",
        "harvest/pick_to_age_1",
        "harvest/upper_half",
        "harvest/break_and_replant",
        "harvest/cave_vines",
    )

    private fun own(path: String): List<Entry> = entries(File(dir, "$path.json").readText())

    /**
     * The values of [tag]: the mod's own files, Cobblemon's from its jar, `#minecraft:crops` as RES8 read it, and `#minecraft:saplings`
     * of Minecraft 1.21.1 with the saplings that the Cobblemon jar adds to it.
     */
    private fun values(tag: String): List<Entry> {
        val namespace = tag.substringBefore(':')
        val path = tag.substringAfter(':')
        return when {
            namespace == "cobblemon_dynamics" -> own(path)

            namespace == "cobblemon" -> entries(Files.readString(CobblemonJar.root.resolve("data/cobblemon/tags/block/$path.json")))

            tag == "minecraft:crops" -> MINECRAFT_CROPS.map { Entry(it, required = true) }

            tag == "minecraft:saplings" -> MINECRAFT_SAPLINGS.map { Entry(it, required = true) } +
                entries(Files.readString(CobblemonJar.root.resolve("data/minecraft/tags/block/saplings.json")))

            else -> error("no tag file for #$tag")
        }
    }

    @Test
    fun `CD-LUM-23 CD-CRP-01 the Cultivator's tag holds the saplings`() {
        assertTrue("#minecraft:saplings" in own("cultivator_targets").map { it.id }, "the Cultivator's tag lacks #minecraft:saplings")
        val grown = resolve("cobblemon_dynamics:cultivator_targets")
        for (sapling in MINECRAFT_SAPLINGS) assertTrue(sapling in grown, "the Cultivator's tag lacks $sapling")
    }

    /**
     * Every block id of [tag], through its nested tags. An optional tag of a mod whose tag files the test does not read, such as
     * `#dynamictrees:saplings`, stays as its `#id`.
     */
    private fun resolve(tag: String): Set<String> = values(tag).flatMapTo(HashSet()) {
        when {
            !it.id.startsWith("#") -> setOf(it.id)
            !it.required && it.id.removePrefix("#").substringBefore(':') !in READ_TAGS -> setOf(it.id)
            else -> resolve(it.id.drop(1))
        }
    }

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

    /**
     * The crop table of ADR-0020, with the ids that Task 20 read from each pinned jar. The Twilight Forest's blocks take the pick by
     * loot table of the data map (CD-FRM-03), and Unusual End's bulb and Actually Additions' coffee the crop adapter of their class.
     */
    @Test
    fun `CD-FRM-01 each family of the crop table of ADR-0020 is in the tags of its roles, and each entry is optional`() {
        val cultivator = resolve("cobblemon_dynamics:cultivator_targets")
        for (block in FRM_GROWN) assertTrue(block in cultivator, "the Cultivator's tag lacks $block")
        for (block in FRM_OREBERRIES) assertFalse(block in cultivator, "the Cultivator's tag holds $block, which bone meal never grows")
        val harvester = resolve("cobblemon_dynamics:harvester_targets")
        for (block in FRM_HARVESTED) assertTrue(block in harvester, "the Harvester's tag lacks $block")
        for (block in FRM_GROWTH_ONLY) assertFalse(block in harvester, "the Harvester's tag holds $block, whose loot lacks its berries")
        for ((path, blocks) in FRM_ADAPTER_TAGS) {
            val tagged = resolve("cobblemon_dynamics:$path")
            for (block in blocks) assertTrue(block in tagged, "#cobblemon_dynamics:$path lacks $block")
        }
        val families = FRM_GROWN + FRM_HARVESTED
        for (path in ownFiles) {
            for (entry in own(path).filter { it.id in families }) assertFalse(entry.required, "${entry.id} in $path.json is required")
        }
    }

    /**
     * R90: a growth-only look-alike whose class is vanilla's `CaveVines` takes the cave-vines adapter by its class alone, with no tag,
     * and its loot is empty. `harvest/never` keeps the Harvester off it even where a pack adds it to the Harvester's tag.
     */
    @Test
    fun `CD-FRM-01 harvest never holds the growth-only look-alikes of a CaveVines class, as optional entries`() {
        val never = own("harvest/never")
        for (block in FRM_GROWTH_ONLY_CAVE_VINES) {
            val entry = never.singleOrNull { it.id == block }
            assertTrue(entry != null, "harvest/never lacks $block")
            assertFalse(entry!!.required, "$block in harvest/never is required")
        }
        assertTrue(FRM_GROWTH_ONLY.containsAll(FRM_GROWTH_ONLY_CAVE_VINES))
    }

    @Test
    fun `CD-FIX-04 the Cultivator's never tag holds Mystical Agriculture's crops as an optional entry`() {
        val entry = own("cultivator/never").single { it.id == "#mysticalagriculture:crops" }
        assertFalse(entry.required, "#mysticalagriculture:crops is required")
    }

    /** The Twilight Forest 4.8.3345 (RES13): the 8 berry bushes and the 4 oreberries, each with its berry table. */
    @Test
    fun `CD-FRM-03 CD-FIX-03 the loot data map gives the 12 Twilight Forest blocks their berry tables and age 2`() {
        val bushes = listOf("blackberry", "blueberry", "raspberry", "skyberry", "maloberry", "blightberry", "stingberry", "duskberry")
        val ores = mapOf("copper" to "copper_oreberry", "iron" to "iron_oreberry", "gold" to "gold_oreberry", "essence" to "essence_berry")
        val expected = bushes.associate { "twilightforest:${it}_bush" to "twilightforest:blocks/${it}_bush_berries" } +
            ores.entries.associate { (ore, table) -> "twilightforest:${ore}_oreberry" to "twilightforest:blocks/${table}_bush_berries" }
        val file = File(dir.parentFile.parentFile, "data_maps/block/harvest_loot.json")
        val values = JsonParser.parseString(file.readText()).asJsonObject.getAsJsonObject("values")
        assertEquals(expected.keys, values.keySet())
        for ((block, table) in expected) {
            val entry = values[block].asJsonObject
            assertEquals(listOf("twilightforest"), RoleConditions.mods(entry), "the condition of $block")
            val value = entry["value"].asJsonObject
            assertEquals(table, value["loot_table"].asString, "the table of $block")
            assertEquals(2, value["age_after"].asInt, "the age after a pick of $block")
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
        /** The namespaces whose tag files [values] reads. */
        val READ_TAGS = setOf("cobblemon_dynamics", "cobblemon", "minecraft")

        private val FRM_PODS = listOf(
            "ars_nouveau:bastion_pod",
            "ars_nouveau:bombegranate_pod",
            "ars_nouveau:frostaya_pod",
            "ars_nouveau:mendosteen_pod",
            "ars_elemental:flashpine_pod",
        )
        private val FRM_BUSHES = "blackberry blueberry raspberry skyberry maloberry blightberry stingberry duskberry".split(" ")
            .map { "twilightforest:${it}_bush" }
        val FRM_OREBERRIES = listOf("copper", "iron", "gold", "essence").map { "twilightforest:${it}_oreberry" }
        private val FRM_FRUIT = listOf("apple_fruit", "green_apple_fruit", "baobab_fruit", "yucca_fruit", "soul_fruit")
            .map { "biomeswevegone:$it" }

        /** The blocks of the five cave-vine look-alikes whose own loot holds their berries, so the cave-vines adapter picks them. */
        private val FRM_PICKED_VINES = listOf(
            "allthemodium:ancient_cavevines",
            "deep_aether:golden_vines",
            "deep_aether:golden_vines_plant",
            "deeperdarker:glowing_vines_plant",
        )

        /**
         * The blocks of the look-alikes that the Cultivator grows and the Harvester leaves (R90), by the reference check of Task 20: the
         * loot of Allthemodium's vine body and of Ars Elemental's source vines is empty, the loot of YUNG's prickly peach cactus is the
         * cactus itself, and Deeper Darker's vine tip has no berries. A pick by their loot would drop nothing or the plant.
         */
        val FRM_GROWTH_ONLY = listOf(
            "allthemodium:ancient_cavevines_plant",
            "ars_elemental:source_vines",
            "ars_elemental:source_vines_plant",
            "yungscavebiomes:prickly_peach_cactus",
            "deeperdarker:glowing_vines",
        )

        /**
         * The growth-only look-alikes whose class is vanilla's `CaveVines`: Allthemodium's `AncientCaveVinesPlant` implements its
         * `ACaveVines`, which extends `CaveVines`, and Ars Elemental's source vines extend `CaveVinesBlock` and `CaveVinesPlantBlock`.
         */
        val FRM_GROWTH_ONLY_CAVE_VINES = listOf(
            "allthemodium:ancient_cavevines_plant",
            "ars_elemental:source_vines",
            "ars_elemental:source_vines_plant",
        )
        private val FRM_OTHERS = listOf("unusualend:gloopilon_bulb", "undergarden:ditchbulb_plant", "actuallyadditions:coffee")

        /** The families of the crop table that the growth adapter takes, Dynamic Trees' by its two tags. */
        val FRM_GROWN = listOf("mega_showdown:max_mushroom") + FRM_PODS + FRM_BUSHES + FRM_FRUIT + "biomeswevegone:white_puffball" +
            FRM_PICKED_VINES + FRM_GROWTH_ONLY + FRM_OTHERS + listOf("#dynamictrees:saplings", "#dynamictrees:rooty_soil")

        /** The families of the crop table that a harvest adapter takes. */
        val FRM_HARVESTED = listOf("mega_showdown:max_mushroom") + FRM_PODS + FRM_BUSHES + FRM_OREBERRIES + FRM_FRUIT +
            "biomeswevegone:white_puffball" + FRM_PICKED_VINES + FRM_OTHERS

        /** The families of the crop table whose harvest adapter is a tag, by the tag's path. */
        val FRM_ADAPTER_TAGS = mapOf(
            "harvest/pick_to_age_1" to listOf("mega_showdown:max_mushroom", "biomeswevegone:white_puffball"),
            "harvest/break_and_replant" to FRM_PODS,
            "harvest/pick_to_age_0" to FRM_FRUIT + "undergarden:ditchbulb_plant",
            "harvest/cave_vines" to FRM_PICKED_VINES,
        )

        /** The members of `#minecraft:saplings` in Minecraft 1.21.1, from the data of its client jar. */
        val MINECRAFT_SAPLINGS = listOf(
            "minecraft:oak_sapling", "minecraft:spruce_sapling", "minecraft:birch_sapling", "minecraft:jungle_sapling",
            "minecraft:acacia_sapling", "minecraft:dark_oak_sapling", "minecraft:azalea", "minecraft:flowering_azalea",
            "minecraft:mangrove_propagule", "minecraft:cherry_sapling",
        )

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
