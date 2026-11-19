package codes.honor.cobblemondynamics.handler.farming

import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.storage.loot.LootTable

/** [count] items of [item], one stack of a harvest's loot. */
data class Drop(val item: ResourceLocation, val count: Int)

/** What a harvest does to one cell. [Clear] leaves the cell's fluid, air or water. */
sealed interface CellChange {
    data object Keep : CellChange

    data object Clear : CellChange

    data class SetAge(val age: Int) : CellChange

    data object ClearBerries : CellChange

    /** The crop's first growth state (CD-HRV-07). */
    data object Replant : CellChange

    /** The first state of another block that a dropped seed places, such as the Aether's stem. */
    data class ReplantAs(val block: ResourceLocation) : CellChange
}

/**
 * CD-HRV-22: the block that a crop's seed item places, which a harvest replants when the loot holds no seed. [Own] is the crop's own
 * block, and [Other] another block of the Cultivator's tag.
 */
sealed interface SeedBlock {
    data object Own : SeedBlock

    data class Other(val block: ResourceLocation) : SeedBlock
}

/** A cell that a harvest changes: the crop's own, the upper half above it, or the fruit that an attached stem faces. */
enum class HarvestCell { CROP, UPPER, FRUIT }

/**
 * The drops and the cell changes of one harvest. [seed] is the item that left the loot for a replant, so that the level drops the
 * other stacks as they are. [upper] is the upper half of a two-block crop, and [fruit] the block that an attached stem faces.
 */
data class HarvestPlan(
    val drops: List<Drop>,
    val crop: CellChange,
    val upper: CellChange = CellChange.Keep,
    val fruit: CellChange = CellChange.Keep,
    val seed: ResourceLocation? = null,
) {
    /** CD-HRV-16: the cells that this harvest breaks, which ask the break question. A pick and a berry tree break none. */
    fun broken(): Set<HarvestCell> = buildSet {
        if (crop == CellChange.Clear || crop == CellChange.Replant || crop is CellChange.ReplantAs) add(HarvestCell.CROP)
        if (upper == CellChange.Clear) add(HarvestCell.UPPER)
        if (fruit == CellChange.Clear) add(HarvestCell.FRUIT)
    }
}

/** The pure harvest plan of each crop adapter (ADR-0015). */
object HarvestPlans {
    /**
     * CD-FRM-03: the loot table that a harvest of [kind] rolls in place of the block loot of the cells that it takes, or null for that
     * block loot. A pick by loot table rolls the entry of the data map, never the bush's own table, which drops the bush (CD-FIX-03).
     */
    fun lootTable(kind: HarvestKind, f: CropFacts): ResourceKey<LootTable>? =
        if (kind == HarvestKind.PICK_BY_LOOT_TABLE) f.harvestLoot?.lootTable else null

    /**
     * [loot] is the loot of the cells that [kind] takes: the crop (and its upper half), the upper half alone, the fruit, or the
     * berries of a berry tree. [ownSeeds] are the dropped items whose `BlockItem.getBlock()` is the crop's own block: wheat seeds,
     * a carrot, a Pam's crop item. `Block.asItem()` alone would miss the Pam's crops, whose `asItem()` is a seed item that never
     * drops (RES8). [seeds] maps each dropped item that places another block of the Cultivator's tag to that block. [survives]
     * tells whether a replant of that block can survive in the crop's cell, where null is the crop's own first state. [hasUpper]
     * tells a crop of the crop adapter with an upper half, such as flax. A berry tree keeps its cell, because
     * `BerryBlockEntity.harvest` already set it back to age 3. [ageAfter] is the `age` that a pick by loot table leaves (CD-FRM-03).
     * [seedBlock] is the block of the crop's seed item, which only the two adapters that break the crop read (CD-HRV-22, CD-HRV-23).
     * [grows] tells whether the first state of that block is a growth stage ([growthStage]); `HarvestApply` always passes it.
     */
    fun plan(
        kind: HarvestKind,
        loot: List<Drop>,
        ownSeeds: Set<ResourceLocation>,
        seeds: Map<ResourceLocation, ResourceLocation>,
        hasUpper: Boolean,
        ageAfter: Int = 0,
        seedBlock: SeedBlock? = null,
        grows: (ResourceLocation?) -> Boolean = { true },
        survives: (ResourceLocation?) -> Boolean,
    ): HarvestPlan = when (kind) {
        HarvestKind.CROP, HarvestKind.BREAK_AND_REPLANT -> breakAndReplant(loot, ownSeeds, seeds, seedBlock, grows, survives, hasUpper)
        HarvestKind.PICK_TO_AGE_0 -> HarvestPlan(loot, CellChange.SetAge(0))
        HarvestKind.PICK_TO_AGE_1 -> HarvestPlan(loot, CellChange.SetAge(1))
        HarvestKind.CAVE_VINES, HarvestKind.CAVE_VINES_TAG -> HarvestPlan(loot, CellChange.ClearBerries)
        HarvestKind.UPPER_HALF -> HarvestPlan(loot, CellChange.Keep, upper = CellChange.Clear)
        HarvestKind.GOURD -> HarvestPlan(loot, CellChange.Keep, fruit = CellChange.Clear)
        HarvestKind.BERRY_TREE -> HarvestPlan(loot, CellChange.Keep)
        HarvestKind.NEVER -> HarvestPlan(emptyList(), CellChange.Keep)
        HarvestKind.PICK_BY_LOOT_TABLE -> HarvestPlan(loot, CellChange.SetAge(ageAfter))
    }

    /** The plan of a crop with no upper half. */
    fun plan(
        kind: HarvestKind,
        loot: List<Drop>,
        ownSeeds: Set<ResourceLocation>,
        seeds: Map<ResourceLocation, ResourceLocation>,
        survives: (ResourceLocation?) -> Boolean,
    ): HarvestPlan = plan(kind, loot, ownSeeds, seeds, hasUpper = false, survives = survives)

    /** The plan of a crop with no upper half, whose seed item places [seedBlock] (CD-HRV-22). */
    fun plan(
        kind: HarvestKind,
        loot: List<Drop>,
        ownSeeds: Set<ResourceLocation>,
        seeds: Map<ResourceLocation, ResourceLocation>,
        seedBlock: SeedBlock?,
        survives: (ResourceLocation?) -> Boolean,
    ): HarvestPlan = plan(kind, loot, ownSeeds, seeds, hasUpper = false, seedBlock = seedBlock, survives = survives)

    /**
     * CD-HRV-22: a created replant must be a growth stage: a state with an `age` of [age] below its largest value [ageMax]. A block
     * with no age, such as a dead bush in a pack's tag, would come back as it was harvested and give loot from nothing on each cycle.
     */
    fun growthStage(age: Int?, ageMax: Int?): Boolean = age != null && ageMax != null && age < ageMax

    /**
     * CD-HRV-22: whether a seed item of another block may create a replant of that block: a block of growth stages, a `CropBlock`
     * ([crop]) or one with an `age` ([hasAge]), and never a sapling or a rooty soil of Dynamic Trees.
     */
    fun replantable(crop: Boolean, hasAge: Boolean, sapling: Boolean, rootySoil: Boolean): Boolean =
        (crop || hasAge) && !sapling && !rootySoil

    /** CD-HRV-08: a harvest that breaks its crop, knows no seed item of it and so leaves the cell empty, which the handler logs. */
    fun noSeedItem(kind: HarvestKind, seedBlock: SeedBlock?, plan: HarvestPlan): Boolean =
        (kind == HarvestKind.CROP || kind == HarvestKind.BREAK_AND_REPLANT) && seedBlock == null && plan.crop == CellChange.Clear

    /**
     * CD-HRV-07, CD-HRV-08, CD-HRV-22: one item of the crop's own seeds replants it, else one item of another block of the Cultivator's
     * tag whose block survives in the cell, else [seedBlock] with no item taken, so the whole loot drops, when its first state [grows].
     * With none of these the cell goes to its fluid and every item drops.
     */
    private fun breakAndReplant(
        loot: List<Drop>,
        ownSeeds: Set<ResourceLocation>,
        seeds: Map<ResourceLocation, ResourceLocation>,
        seedBlock: SeedBlock?,
        grows: (ResourceLocation?) -> Boolean,
        survives: (ResourceLocation?) -> Boolean,
        hasUpper: Boolean,
    ): HarvestPlan {
        val upper = if (hasUpper) CellChange.Clear else CellChange.Keep
        val own = loot.indexOfFirst { it.count > 0 && it.item in ownSeeds }
        if (own >= 0 && survives(null)) return HarvestPlan(minusOne(loot, own), CellChange.Replant, upper, seed = loot[own].item)
        val other = loot.indexOfFirst { drop -> drop.count > 0 && seeds[drop.item]?.let(survives) == true }
        if (other >= 0) {
            val item = loot[other].item
            return HarvestPlan(minusOne(loot, other), CellChange.ReplantAs(seeds.getValue(item)), upper, seed = item)
        }
        val created = when (seedBlock) {
            SeedBlock.Own -> CellChange.Replant.takeIf { grows(null) && survives(null) }
            is SeedBlock.Other -> CellChange.ReplantAs(seedBlock.block).takeIf { grows(seedBlock.block) && survives(seedBlock.block) }
            null -> null
        }
        return HarvestPlan(loot, created ?: CellChange.Clear, upper)
    }

    /** [loot] with one item fewer in the stack at [index]. A stack that this empties leaves the list. */
    private fun minusOne(loot: List<Drop>, index: Int): List<Drop> = loot.mapIndexedNotNull { i, drop ->
        if (i != index) drop else drop.copy(count = drop.count - 1).takeIf { it.count > 0 }
    }
}
