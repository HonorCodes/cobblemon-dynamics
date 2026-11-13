package codes.honor.cobblemondynamics.handler.farming

/** The pure choice of a crop adapter from the facts of a block (ADR-0015). */
object CropAdapters {
    /** CD-CRP-03, CD-HRV-03: the first harvest adapter that claims the block, or null for none (CD-CRP-04). */
    fun harvestKind(f: CropFacts): HarvestKind? = when {
        f.apricorn || HarvestKind.NEVER in f.adapterTags -> null

        else -> HarvestKind.entries.firstOrNull { it.tag != null && it in f.adapterTags } ?: when {
            f.berryTree -> HarvestKind.BERRY_TREE
            f.caveVines -> HarvestKind.CAVE_VINES
            f.attachedStem -> HarvestKind.GOURD
            f.cropBlock -> HarvestKind.CROP
            else -> null
        }
    }

    /** CD-CUL-02: the growth adapter claims every `BonemealableBlock`. */
    fun grows(f: CropFacts): Boolean = f.bonemealable

    /** CD-CRP-05: the Harvester's adapter of a block for the load line. `harvest/never` and an apricorn count as `never`. */
    fun harvestAdapterName(f: CropFacts): String? =
        if (f.apricorn || HarvestKind.NEVER in f.adapterTags) NEVER else harvestKind(f)?.name?.lowercase()

    /** CD-CRP-05: the Cultivator's adapter of a block for the load line. */
    fun growthAdapterName(f: CropFacts): String? = if (grows(f)) GROWTH else null

    private const val NEVER = "never"
    private const val GROWTH = "growth"
}
