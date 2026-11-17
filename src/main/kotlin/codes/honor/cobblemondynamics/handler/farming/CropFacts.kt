package codes.honor.cobblemondynamics.handler.farming

/**
 * How the Harvester takes one crop (ADR-0015). The pick by loot table of the data map `cobblemon_dynamics:harvest_loot` comes first
 * (CD-FRM-03), then the five kinds with a tag, then the kinds of a class (CD-CRP-03).
 */
enum class HarvestKind(val tag: String?) {
    NEVER("harvest/never"),
    PICK_TO_AGE_0("harvest/pick_to_age_0"),
    PICK_TO_AGE_1("harvest/pick_to_age_1"),
    UPPER_HALF("harvest/upper_half"),
    BREAK_AND_REPLANT("harvest/break_and_replant"),
    BERRY_TREE(null),
    CAVE_VINES(null),
    GOURD(null),
    CROP(null),
    PICK_BY_LOOT_TABLE(null),
}

/** What the adapters read of one block state. [CropFactsReader] fills it from a level, and the unit tests build it by hand. */
data class CropFacts(
    val adapterTags: Set<HarvestKind> = emptySet(),
    /** `ApricornBlock`, or `#cobblemon:apricorns`. */
    val apricorn: Boolean = false,
    val cropBlock: Boolean = false,
    val bonemealable: Boolean = false,
    /** Cobblemon's `BerryBlock`. */
    val berryTree: Boolean = false,
    val caveVines: Boolean = false,
    val attachedStem: Boolean = false,
    /** The value of the `IntegerProperty` named `age`, with its lowest and highest possible values, or null with no such property. */
    val age: Int? = null,
    val ageMin: Int = 0,
    val ageMax: Int = 0,
    /** `CropBlock.isMaxAge`, for a `CropBlock`. */
    val cropMaxAge: Boolean = false,
    /** The state has `half` = upper. */
    val upperHalf: Boolean = false,
    /** A lower half with its upper half above it. */
    val upperPresent: Boolean = false,
    /** `CaveVines.BERRIES`. */
    val berries: Boolean = false,
    /** The block's entry in the data map `cobblemon_dynamics:harvest_loot`, or null. */
    val harvestLoot: HarvestLoot? = null,
    /** False for a block of `#cobblemon_dynamics:cultivator/never`, whose bone meal never grows it (CD-FIX-04). */
    val growable: Boolean = true,
)
