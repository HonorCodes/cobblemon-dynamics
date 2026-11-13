package codes.honor.cobblemondynamics.handler.farming

import net.minecraft.resources.ResourceLocation

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
 * The drops and the cell changes of one harvest. [seed] is the item that left the loot for a replant, so that the level drops the
 * other stacks as they are. [upper] is the upper half of a two-block crop, and [fruit] the block that an attached stem faces.
 */
data class HarvestPlan(
    val drops: List<Drop>,
    val crop: CellChange,
    val upper: CellChange = CellChange.Keep,
    val fruit: CellChange = CellChange.Keep,
    val seed: ResourceLocation? = null,
)

/** The pure harvest plan of each crop adapter (ADR-0015). */
object HarvestPlans {
    /**
     * [loot] is the loot of the cells that [kind] takes: the crop (and its upper half), the upper half alone, the fruit, or the
     * berries of a berry tree. [ownSeeds] are the dropped items whose `BlockItem.getBlock()` is the crop's own block: wheat seeds,
     * a carrot, a Pam's crop item. `Block.asItem()` alone would miss the Pam's crops, whose `asItem()` is a seed item that never
     * drops (RES8). [seeds] maps each dropped item that places another block of the Cultivator's tag to that block. [survives]
     * tells whether a replant of that block can survive in the crop's cell, where null is the crop's own first state. [hasUpper]
     * tells a crop of the crop adapter with an upper half, such as flax. A berry tree keeps its cell, because
     * `BerryBlockEntity.harvest` already set it back to age 3.
     */
    fun plan(
        kind: HarvestKind,
        loot: List<Drop>,
        ownSeeds: Set<ResourceLocation>,
        seeds: Map<ResourceLocation, ResourceLocation>,
        hasUpper: Boolean,
        survives: (ResourceLocation?) -> Boolean,
    ): HarvestPlan = when (kind) {
        HarvestKind.CROP, HarvestKind.BREAK_AND_REPLANT -> breakAndReplant(loot, ownSeeds, seeds, survives, hasUpper)
        HarvestKind.PICK_TO_AGE_0 -> HarvestPlan(loot, CellChange.SetAge(0))
        HarvestKind.PICK_TO_AGE_1 -> HarvestPlan(loot, CellChange.SetAge(1))
        HarvestKind.CAVE_VINES -> HarvestPlan(loot, CellChange.ClearBerries)
        HarvestKind.UPPER_HALF -> HarvestPlan(loot, CellChange.Keep, upper = CellChange.Clear)
        HarvestKind.GOURD -> HarvestPlan(loot, CellChange.Keep, fruit = CellChange.Clear)
        HarvestKind.BERRY_TREE -> HarvestPlan(loot, CellChange.Keep)
        HarvestKind.NEVER -> HarvestPlan(emptyList(), CellChange.Keep)
    }

    /** The plan of a crop with no upper half. */
    fun plan(
        kind: HarvestKind,
        loot: List<Drop>,
        ownSeeds: Set<ResourceLocation>,
        seeds: Map<ResourceLocation, ResourceLocation>,
        survives: (ResourceLocation?) -> Boolean,
    ): HarvestPlan = plan(kind, loot, ownSeeds, seeds, hasUpper = false, survives = survives)

    /**
     * CD-HRV-07, CD-HRV-08: one item of the crop's own seeds replants it, else one item of another block of the Cultivator's tag
     * whose block survives in the cell, else the cell goes to its fluid and every item drops. No seed comes from nothing.
     */
    private fun breakAndReplant(
        loot: List<Drop>,
        ownSeeds: Set<ResourceLocation>,
        seeds: Map<ResourceLocation, ResourceLocation>,
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
        return HarvestPlan(loot, CellChange.Clear, upper)
    }

    /** [loot] with one item fewer in the stack at [index]. A stack that this empties leaves the list. */
    private fun minusOne(loot: List<Drop>, index: Int): List<Drop> = loot.mapIndexedNotNull { i, drop ->
        if (i != index) drop else drop.copy(count = drop.count - 1).takeIf { it.count > 0 }
    }
}
