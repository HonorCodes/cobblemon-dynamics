package codes.honor.cobblemondynamics.handler.farming

import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/** The loot of each case comes from the adapter table of ADR-0015. */
class HarvestPlanTest {
    private val wheat = ResourceLocation.parse("minecraft:wheat")
    private val seeds = ResourceLocation.parse("minecraft:wheat_seeds")
    private val always = { _: ResourceLocation? -> true }

    @Test
    fun `CD-HRV-07 wheat keeps one seed back and replants`() {
        val plan = HarvestPlans.plan(HarvestKind.CROP, listOf(Drop(wheat, 1), Drop(seeds, 3)), setOf(seeds), emptyMap(), always)
        assertEquals(listOf(Drop(wheat, 1), Drop(seeds, 2)), plan.drops)
        assertEquals(CellChange.Replant, plan.crop)
        assertEquals(seeds, plan.seed)
    }

    @Test
    fun `CD-HRV-07 a Pam's crop replants from its dropped crop item, not from its asItem seed`() {
        val tomato = ResourceLocation.parse("pamhc2crops:tomatoitem")
        val plan = HarvestPlans.plan(HarvestKind.CROP, listOf(Drop(tomato, 3)), setOf(tomato), emptyMap(), always)
        assertEquals(listOf(Drop(tomato, 2)), plan.drops)
        assertEquals(CellChange.Replant, plan.crop)
    }

    @Test
    fun `CD-HRV-07 carrots replant from the crop's own item`() {
        val carrot = ResourceLocation.parse("minecraft:carrot")
        val plan = HarvestPlans.plan(HarvestKind.CROP, listOf(Drop(carrot, 3)), setOf(carrot), emptyMap(), always)
        assertEquals(listOf(Drop(carrot, 2)), plan.drops)
        assertEquals(CellChange.Replant, plan.crop)
    }

    @Test
    fun `CD-HRV-08 CD-HRV-22 cabbage loot with no seed replants from its seed block, and with no seed block clears the cell`() {
        val cabbage = ResourceLocation.parse("farmersdelight:cabbage")
        val cabbageSeeds = ResourceLocation.parse("farmersdelight:cabbage_seeds")
        val loot = listOf(Drop(cabbage, 1))
        val created = HarvestPlans.plan(HarvestKind.CROP, loot, setOf(cabbageSeeds), emptyMap(), SeedBlock.Own, always)
        assertEquals(loot, created.drops)
        assertEquals(CellChange.Replant, created.crop)
        assertEquals(null, created.seed, "a created replant takes an item from the loot")
        val none = HarvestPlans.plan(HarvestKind.CROP, loot, setOf(cabbageSeeds), emptyMap(), survives = always)
        assertEquals(loot, none.drops)
        assertEquals(CellChange.Clear, none.crop)
        assertEquals(null, none.seed)
    }

    @Test
    fun `CD-HRV-08 a replant that cannot survive keeps every item and plans an empty cell`() {
        val loot = listOf(Drop(wheat, 1), Drop(seeds, 2))
        val dry = HarvestPlans.plan(HarvestKind.CROP, loot, setOf(seeds), emptyMap(), SeedBlock.Own) { false }
        assertEquals(CellChange.Clear, dry.crop)
        assertEquals(loot, dry.drops)
        assertEquals(null, dry.seed)
        val stem = ResourceLocation.parse("aether:berry_bush_stem")
        val other = HarvestPlans.plan(
            HarvestKind.BREAK_AND_REPLANT,
            listOf(Drop(wheat, 2)),
            emptySet(),
            emptyMap(),
            SeedBlock.Other(stem),
        ) {
            it != stem
        }
        assertEquals(CellChange.Clear, other.crop, "a seed block of another block that cannot survive")
        assertEquals(listOf(Drop(wheat, 2)), other.drops)
    }

    @Test
    fun `CD-HRV-22 wheat loot of 2 wheat and no seed plans the replant and keeps both wheat`() {
        val plan = HarvestPlans.plan(HarvestKind.CROP, listOf(Drop(wheat, 2)), setOf(seeds), emptyMap(), SeedBlock.Own, always)
        assertEquals(listOf(Drop(wheat, 2)), plan.drops)
        assertEquals(CellChange.Replant, plan.crop)
        assertEquals(null, plan.seed)
    }

    @Test
    fun `CD-HRV-22 loot with seeds still takes one seed`() {
        val plan = HarvestPlans.plan(
            HarvestKind.CROP,
            listOf(Drop(wheat, 1), Drop(seeds, 3)),
            setOf(seeds),
            emptyMap(),
            SeedBlock.Own,
            always,
        )
        assertEquals(listOf(Drop(wheat, 1), Drop(seeds, 2)), plan.drops)
        assertEquals(CellChange.Replant, plan.crop)
        assertEquals(seeds, plan.seed)
    }

    @Test
    fun `CD-HRV-22 a seed item whose block is another block of the Cultivator's tag plans that block`() {
        val stem = ResourceLocation.parse("aether:berry_bush_stem")
        val berry = ResourceLocation.parse("aether:blue_berry")
        val plan = HarvestPlans.plan(
            HarvestKind.BREAK_AND_REPLANT,
            listOf(Drop(berry, 2)),
            emptySet(),
            emptyMap(),
            SeedBlock.Other(stem),
            always,
        )
        assertEquals(CellChange.ReplantAs(stem), plan.crop)
        assertEquals(listOf(Drop(berry, 2)), plan.drops)
        assertEquals(null, plan.seed)
    }

    /** Decision 7 of the 1.2.1 plan: a dropped seed of another block of the tag wins over a created seed of the crop's own block. */
    @Test
    fun `CD-HRV-22 a dropped seed of another block of the tag wins over a created own seed`() {
        val stem = ResourceLocation.parse("aether:berry_bush_stem")
        val loot = listOf(Drop(wheat, 1), Drop(stem, 1))
        val plan = HarvestPlans.plan(HarvestKind.BREAK_AND_REPLANT, loot, emptySet(), mapOf(stem to stem), SeedBlock.Own, always)
        assertEquals(CellChange.ReplantAs(stem), plan.crop)
        assertEquals(stem, plan.seed)
        assertEquals(listOf(Drop(wheat, 1)), plan.drops)
    }

    @Test
    fun `CD-HRV-23 the adapters that keep the crop's block never break or replant its cell, with or without seeds`() {
        val keep = listOf(
            HarvestKind.PICK_TO_AGE_0,
            HarvestKind.PICK_TO_AGE_1,
            HarvestKind.CAVE_VINES,
            HarvestKind.CAVE_VINES_TAG,
            HarvestKind.UPPER_HALF,
            HarvestKind.GOURD,
            HarvestKind.BERRY_TREE,
            HarvestKind.PICK_BY_LOOT_TABLE,
        )
        val loots = listOf(listOf(Drop(wheat, 2)), listOf(Drop(wheat, 1), Drop(seeds, 2)))
        for (kind in keep) {
            for (loot in loots) {
                for (seedBlock in listOf(null, SeedBlock.Own)) {
                    val plan = HarvestPlans.plan(
                        kind,
                        loot,
                        setOf(seeds),
                        emptyMap(),
                        hasUpper = false,
                        seedBlock = seedBlock,
                        survives = always,
                    )
                    val broken = plan.crop == CellChange.Clear || plan.crop == CellChange.Replant || plan.crop is CellChange.ReplantAs
                    assertFalse(broken, "$kind with $loot and $seedBlock plans ${plan.crop}")
                    assertFalse(HarvestCell.CROP in plan.broken(), "$kind breaks its crop cell")
                    assertEquals(null, plan.seed, "$kind takes a seed")
                }
            }
        }
    }

    @Test
    fun `CD-HRV-07 the Aether's grown bush replants its stem from the drops`() {
        val stem = ResourceLocation.parse("aether:berry_bush_stem")
        val berry = ResourceLocation.parse("aether:blue_berry")
        val loot = listOf(Drop(berry, 2), Drop(stem, 1))
        val plan = HarvestPlans.plan(HarvestKind.BREAK_AND_REPLANT, loot, emptySet(), mapOf(stem to stem), always)
        assertEquals(listOf(Drop(berry, 2)), plan.drops)
        assertEquals(CellChange.ReplantAs(stem), plan.crop)
        assertEquals(stem, plan.seed)
        // The stem cannot survive in the cell, so everything drops.
        val dry = HarvestPlans.plan(HarvestKind.BREAK_AND_REPLANT, loot, emptySet(), mapOf(stem to stem)) { it == null }
        assertEquals(CellChange.Clear, dry.crop)
        assertEquals(loot, dry.drops)
    }

    @Test
    fun `CD-HRV-07 cocoa replants on the same cell from its own beans`() {
        val beans = ResourceLocation.parse("minecraft:cocoa_beans")
        val plan = HarvestPlans.plan(HarvestKind.BREAK_AND_REPLANT, listOf(Drop(beans, 3)), setOf(beans), emptyMap(), always)
        assertEquals(listOf(Drop(beans, 2)), plan.drops)
        assertEquals(CellChange.Replant, plan.crop)
    }

    @Test
    fun `CD-HRV-09 picks keep the plant`() {
        val berries = ResourceLocation.parse("minecraft:sweet_berries")
        val bush = HarvestPlans.plan(HarvestKind.PICK_TO_AGE_1, listOf(Drop(berries, 3)), setOf(berries), emptyMap(), always)
        assertEquals(CellChange.SetAge(1), bush.crop)
        assertEquals(listOf(Drop(berries, 3)), bush.drops)
        val apple = ResourceLocation.parse("minecraft:apple")
        val fruit = HarvestPlans.plan(HarvestKind.PICK_TO_AGE_0, listOf(Drop(apple, 1)), emptySet(), emptyMap(), always)
        assertEquals(CellChange.SetAge(0), fruit.crop)
        val glow = ResourceLocation.parse("minecraft:glow_berries")
        val vines = HarvestPlans.plan(HarvestKind.CAVE_VINES, listOf(Drop(glow, 1)), setOf(glow), emptyMap(), always)
        assertEquals(CellChange.ClearBerries, vines.crop)
        assertEquals(listOf(Drop(glow, 1)), vines.drops)
    }

    @Test
    fun `CD-HRV-10 CD-HRV-12 an upper half or a fruit leaves the plant's own cell alone`() {
        val grain = ResourceLocation.parse("cobblemon:hearty_grains")
        val upper = HarvestPlans.plan(HarvestKind.UPPER_HALF, listOf(Drop(grain, 3)), setOf(grain), emptyMap(), always)
        assertEquals(CellChange.Keep, upper.crop)
        assertEquals(CellChange.Clear, upper.upper)
        assertEquals(listOf(Drop(grain, 3)), upper.drops)
        val slice = ResourceLocation.parse("minecraft:melon_slice")
        val gourd = HarvestPlans.plan(HarvestKind.GOURD, listOf(Drop(slice, 5)), emptySet(), emptyMap(), always)
        assertEquals(CellChange.Keep, gourd.crop)
        assertEquals(CellChange.Clear, gourd.fruit)
        assertEquals(listOf(Drop(slice, 5)), gourd.drops)
    }

    @Test
    fun `CD-HRV-07 a two-block crop of the crop adapter clears its upper half and replants the lower`() {
        val flax = ResourceLocation.parse("supplementaries:flax")
        val flaxSeeds = ResourceLocation.parse("supplementaries:flax_seeds")
        val loot = listOf(Drop(flax, 1), Drop(flaxSeeds, 2))
        val plan = HarvestPlans.plan(HarvestKind.CROP, loot, setOf(flaxSeeds), emptyMap(), hasUpper = true, survives = always)
        assertEquals(CellChange.Replant, plan.crop)
        assertEquals(CellChange.Clear, plan.upper)
        assertEquals(listOf(Drop(flax, 1), Drop(flaxSeeds, 1)), plan.drops)
    }

    @Test
    fun `CD-HRV-11 a berry tree drops its berries and keeps the state that its harvest set`() {
        val oran = ResourceLocation.parse("cobblemon:oran_berry")
        val plan = HarvestPlans.plan(HarvestKind.BERRY_TREE, listOf(Drop(oran, 4)), emptySet(), emptyMap(), always)
        assertEquals(CellChange.Keep, plan.crop)
        assertEquals(listOf(Drop(oran, 4)), plan.drops)
    }

    @Test
    fun `CD-HRV-16 each kind names the cells that it breaks`() {
        val crop = HarvestPlans.plan(HarvestKind.CROP, listOf(Drop(wheat, 1), Drop(seeds, 2)), setOf(seeds), emptyMap(), always)
        assertEquals(setOf(HarvestCell.CROP), crop.broken())
        val flax = HarvestPlans.plan(HarvestKind.CROP, listOf(Drop(wheat, 1)), emptySet(), emptyMap(), hasUpper = true, survives = always)
        assertEquals(setOf(HarvestCell.CROP, HarvestCell.UPPER), flax.broken())
        val cocoa = HarvestPlans.plan(HarvestKind.BREAK_AND_REPLANT, listOf(Drop(seeds, 1)), setOf(seeds), emptyMap(), always)
        assertEquals(setOf(HarvestCell.CROP), cocoa.broken())
        val grains = HarvestPlans.plan(HarvestKind.UPPER_HALF, listOf(Drop(wheat, 1)), emptySet(), emptyMap(), always)
        assertEquals(setOf(HarvestCell.UPPER), grains.broken())
        val gourd = HarvestPlans.plan(HarvestKind.GOURD, listOf(Drop(wheat, 1)), emptySet(), emptyMap(), always)
        assertEquals(setOf(HarvestCell.FRUIT), gourd.broken())
        for (pick in listOf(HarvestKind.PICK_TO_AGE_0, HarvestKind.PICK_TO_AGE_1, HarvestKind.CAVE_VINES, HarvestKind.BERRY_TREE)) {
            val plan = HarvestPlans.plan(pick, listOf(Drop(wheat, 1)), emptySet(), emptyMap(), always)
            assertEquals(emptySet<HarvestCell>(), plan.broken(), "$pick")
        }
    }

    /** The Twilight Forest 4.8.3345: the bush's own loot table drops the bush, and its pick rolls the berry table (RES13). */
    @Test
    fun `CD-FIX-03 CD-FRM-03 a grown blackberry bush drops the loot of its berry table only, and goes to age 2`() {
        val berries = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse("twilightforest:blocks/blackberry_bush_berries"))
        val bush = ResourceLocation.parse("twilightforest:blackberry_bush")
        val blackberry = ResourceLocation.parse("twilightforest:blackberry")
        val facts = CropFacts(bonemealable = true, age = 3, ageMax = 3, harvestLoot = HarvestLoot(berries, 2))
        val kind = CropAdapters.harvestKind(facts)
        assertEquals(HarvestKind.PICK_BY_LOOT_TABLE, kind)
        // Null stands for the block's own loot table, which the level rolls for every other kind.
        val tables = mapOf(null to listOf(Drop(bush, 1)), berries to listOf(Drop(blackberry, 1)))
        val source = HarvestPlans.lootTable(kind!!, facts)
        assertEquals(berries, source, "the harvest rolls another table than the berry table")
        val plan = HarvestPlans.plan(
            kind,
            tables.getValue(source),
            emptySet(),
            emptyMap(),
            hasUpper = false,
            survives = always,
            ageAfter = 2,
        )
        assertEquals(listOf(Drop(blackberry, 1)), plan.drops)
        assertFalse(plan.drops.any { it.item == bush }, "the harvest drops the bush")
        assertEquals(CellChange.SetAge(2), plan.crop)
        assertEquals(emptySet<HarvestCell>(), plan.broken(), "the pick breaks a cell")
        assertEquals(null, HarvestPlans.lootTable(HarvestKind.PICK_TO_AGE_0, facts), "a pick of a tag rolls another table")
    }
}
