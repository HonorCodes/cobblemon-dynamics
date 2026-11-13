package codes.honor.cobblemondynamics.handler.farming

import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
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
    fun `CD-HRV-08 cabbage with no seed, and a replant that cannot survive, drop everything and clear the cell`() {
        val cabbage = ResourceLocation.parse("farmersdelight:cabbage")
        val cabbageSeeds = ResourceLocation.parse("farmersdelight:cabbage_seeds")
        val none = HarvestPlans.plan(HarvestKind.CROP, listOf(Drop(cabbage, 1)), setOf(cabbageSeeds), emptyMap(), always)
        assertEquals(listOf(Drop(cabbage, 1)), none.drops)
        assertEquals(CellChange.Clear, none.crop)
        assertEquals(null, none.seed)
        val dry = HarvestPlans.plan(HarvestKind.CROP, listOf(Drop(wheat, 1), Drop(seeds, 2)), setOf(seeds), emptyMap()) { false }
        assertEquals(CellChange.Clear, dry.crop)
        assertEquals(3, dry.drops.sumOf { it.count })
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
}
