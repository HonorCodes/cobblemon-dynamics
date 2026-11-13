package codes.honor.cobblemondynamics.handler.farming

/** When a crop is fully grown, and when the Cultivator still has work on it (ADR-0015). */
object CropRules {
    /**
     * CD-HRV-04: whether the harvest adapter [kind] takes the crop now. A berry tree is grown at the top of its age property, which is
     * Cobblemon's `FRUIT_AGE` of 5 (RES8), so no Cobblemon class loads in a unit test.
     */
    fun isFullyGrown(kind: HarvestKind, f: CropFacts): Boolean = when (kind) {
        HarvestKind.NEVER -> false
        HarvestKind.PICK_TO_AGE_0, HarvestKind.PICK_TO_AGE_1 -> f.age != null && f.age == f.ageMax
        HarvestKind.UPPER_HALF -> f.age != null && f.age == f.ageMax && f.upperPresent
        HarvestKind.BREAK_AND_REPLANT -> f.age == null || f.age == f.ageMax
        HarvestKind.BERRY_TREE -> f.age != null && f.age == f.ageMax
        HarvestKind.CAVE_VINES -> f.berries
        HarvestKind.GOURD -> true
        HarvestKind.CROP -> f.cropMaxAge
    }

    /**
     * CD-CUL-03: bonemeal can still change the crop, and the Harvester would not take it yet. So the Cultivator stops where the
     * Harvester starts, and never ripens an oddion into its hatch.
     */
    fun cultivatorDemand(validTarget: Boolean, kind: HarvestKind?, f: CropFacts): Boolean =
        validTarget && (kind == null || !isFullyGrown(kind, f))
}
