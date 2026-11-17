package codes.honor.cobblemondynamics.handler.farming

/** The pure choice of a crop adapter from the facts of a block (ADR-0015). */
object CropAdapters {
    /**
     * CD-CRP-03, CD-HRV-03: the first harvest adapter that claims the block, or null for none (CD-CRP-04). An entry of the loot data
     * map comes before every adapter tag (CD-FRM-03), and an entry that cannot pick leaves the block with no adapter.
     */
    fun harvestKind(f: CropFacts): HarvestKind? = when {
        f.apricorn -> null

        f.harvestLoot != null -> if (picksByLootTable(f)) HarvestKind.PICK_BY_LOOT_TABLE else null

        HarvestKind.NEVER in f.adapterTags -> null

        else -> HarvestKind.entries.firstOrNull { it.tag != null && it in f.adapterTags } ?: when {
            f.berryTree -> HarvestKind.BERRY_TREE
            f.caveVines -> HarvestKind.CAVE_VINES
            f.attachedStem -> HarvestKind.GOURD
            f.cropBlock -> HarvestKind.CROP
            else -> null
        }
    }

    /**
     * CD-FRM-03: a pick by loot table needs an `age` and an `age_after` below its highest value. A block with no `age` is never fully
     * grown, and a higher `age_after` leaves the block fully grown after each pick, so the Harvester would pick it on each harvest.
     */
    fun picksByLootTable(f: CropFacts): Boolean {
        val loot = f.harvestLoot ?: return false
        return f.age != null && loot.ageAfter < f.ageMax
    }

    /** CD-CUL-02, CD-FIX-04: the growth adapter claims every `BonemealableBlock` outside the Cultivator's never tag. */
    fun grows(f: CropFacts): Boolean = f.bonemealable && f.growable

    /** CD-CRP-05: the Harvester's adapter of a block for the load line. `harvest/never` and an apricorn count as `never`. */
    fun harvestAdapterName(f: CropFacts): String? {
        val kind = harvestKind(f)
        return if (kind == null && (f.apricorn || HarvestKind.NEVER in f.adapterTags)) NEVER else kind?.name?.lowercase()
    }

    /** CD-CRP-05: the Cultivator's adapter of a block for the load line. */
    fun growthAdapterName(f: CropFacts): String? = if (grows(f)) GROWTH else null

    private const val NEVER = "never"
    private const val GROWTH = "growth"
}
