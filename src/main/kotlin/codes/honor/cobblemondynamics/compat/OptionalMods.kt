package codes.honor.cobblemondynamics.compat

/**
 * One mod of the table of ADR-0016 that the code supports (CD-OPT-05, CD-OPT-11). [compat] names its packages `compat/<compat>/`
 * and `mixin/compat/<compat>/`, which is the mod id except for `compat/apothic/` of 1.1. A mod with no [compat] works through
 * vanilla classes, block ids and tags alone, so no source names its classes.
 */
data class OptionalMod(
    val modId: String,
    /** The package prefixes of the mod as class file paths, such as `blusunrize/`, which only its compat and mixin packages name. */
    val packages: List<String>,
    /** The adapters that its compat package registers, each found or missing after a tag load. */
    val adapters: List<String> = emptyList(),
    val compat: String? = modId,
)

/**
 * The optional mods of ADR-0016 that the code names, each a compile-only dependency of the build at its pin of the 1.2 plan, and
 * the crop mods of the table, which no source names (CD-CRP-19). The package map of `MixinPlugin`, the list of `verifyJar` in
 * `build.gradle` and `neoforge.mods.toml` hold the same mods: `MixinPluginTest`, `CompatImportsTest` and `ModsTomlTest` keep
 * them in step. The package prefixes come from the class files of each pinned jar.
 */
object OptionalMods {
    val ALL: List<OptionalMod> = listOf(
        OptionalMod("immersiveengineering", listOf("blusunrize/")),
        OptionalMod("mekanism", listOf("mekanism/")),
        OptionalMod("mekanismgenerators", listOf("mekanism/generators/")),
        OptionalMod("railcraft", listOf("mods/railcraft/")),
        OptionalMod("pneumaticcraft", listOf("me/desht/pneumaticcraft/")),
        // The classes of Botany Pots extend those of its library Bookshelf, which only Botany Pots' compat package may name.
        OptionalMod("botanypots", listOf("net/darkhax/botanypots/", "net/darkhax/bookshelf/")),
        OptionalMod("ironfurnaces", listOf("ironfurnaces/")),
        OptionalMod("cookingforblockheads", listOf("net/blay09/mods/cookingforblockheads/")),
        OptionalMod("forbidden_arcanus", listOf("com/stal111/forbidden_arcanus/")),
        OptionalMod("cobblemon_incubator", listOf("com/nbp/cobblemon_incubator/")),
        OptionalMod("ars_nouveau", listOf("com/hollingsworth/")),
        OptionalMod("createaddition", listOf("com/mrh0/createaddition/")),
        OptionalMod("create_enchantment_industry", listOf("plus/dragons/createenchantmentindustry/")),
        OptionalMod("irons_spellbooks", listOf("io/redspace/ironsspellbooks/")),
        OptionalMod("easymagic", listOf("fuzs/easymagic/"), listOf("muse_table")),
        OptionalMod("enchantinginfuser", listOf("fuzs/enchantinginfuser/"), listOf("muse_infusers")),
        // CD-XPC-11: the XP capability on the outputs of the Experience Collector.
        OptionalMod("experiencelib", listOf("es/degrassi/experiencelib/"), listOf("collector_xp")),
        OptionalMod("dynamictrees", listOf("com/dtteam/dynamictrees/"), listOf("lumberjack_fell")),
        OptionalMod("apothic_enchanting", listOf("dev/shadowsoffire/"), compat = "apothic"),
        // CD-CRY-05: only compat/ae2/ may name AE2.
        OptionalMod("ae2", listOf("appeng/")),
        OptionalMod("farmersdelight", listOf("vectorwing/"), compat = null),
        OptionalMod("aether", listOf("com/aetherteam/"), compat = null),
        OptionalMod("deep_aether", listOf("io/github/razordevs/"), compat = null),
        OptionalMod("biomeswevegone", listOf("net/potionstudios/"), compat = null),
        OptionalMod("undergarden", listOf("quek/undergarden/"), compat = null),
        // The mods of the fixes of 1.2 that tags and data maps alone reach (ADR-0020).
        OptionalMod("twilightforest", listOf("twilightforest/"), compat = null),
        OptionalMod("mysticalagriculture", listOf("com/blakebr0/mysticalagriculture/"), compat = null),
        OptionalMod("createdieselgenerators", listOf("com/jesz/createdieselgenerators/"), compat = null),
        OptionalMod("create_new_age", listOf("org/antarcticgardens/cna/"), compat = null),
    )
}
