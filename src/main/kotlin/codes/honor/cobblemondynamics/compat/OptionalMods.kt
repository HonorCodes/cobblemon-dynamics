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
        OptionalMod("immersiveengineering", listOf("blusunrize/"), listOf("ie_furnace", "ie_watermill", "ie_windmill")),
        OptionalMod("mekanism", listOf("mekanism/"), listOf("fuelwood_heater", "mekanism_heat")),
        OptionalMod("mekanismgenerators", listOf("mekanism/generators/"), listOf("wind_generator")),
        OptionalMod("railcraft", listOf("mods/railcraft/"), listOf("railcraft_fuel", "railcraft_firebox")),
        OptionalMod("pneumaticcraft", listOf("me/desht/pneumaticcraft/"), listOf("pnc_compressor", "pnc_heat", "pnc_air")),
        // The classes of Botany Pots extend those of its library Bookshelf, which only Botany Pots' compat package may name.
        OptionalMod("botanypots", listOf("net/darkhax/botanypots/", "net/darkhax/bookshelf/"), listOf("pot_cultivator", "pot_harvester")),
        OptionalMod("ironfurnaces", listOf("ironfurnaces/"), listOf("iron_furnace")),
        OptionalMod("cookingforblockheads", listOf("net/blay09/mods/cookingforblockheads/"), listOf("cfb_oven")),
        OptionalMod("forbidden_arcanus", listOf("com/stal111/forbidden_arcanus/"), listOf("clibano")),
        OptionalMod("cobblemon_incubator", listOf("com/nbp/cobblemon_incubator/"), listOf("egg_incubator")),
        OptionalMod("ars_nouveau", listOf("com/hollingsworth/"), listOf("volcanic_sourcelink")),
        OptionalMod("createaddition", listOf("com/mrh0/createaddition/"), listOf("liquid_burner")),
        // Its hook finds the crank wheels' block entity type by id, so no source names its classes (CD-KIN-05).
        OptionalMod("create_connected", listOf("com/hlysine/create_connected/"), listOf("crank_wheel")),
        OptionalMod("create_enchantment_industry", listOf("plus/dragons/createenchantmentindustry/"), listOf("muse_infuser")),
        OptionalMod("irons_spellbooks", listOf("io/redspace/ironsspellbooks/"), listOf("alchemist_cauldron")),
        OptionalMod("easymagic", listOf("fuzs/easymagic/"), listOf("muse_table")),
        OptionalMod("enchantinginfuser", listOf("fuzs/enchantinginfuser/"), listOf("muse_infusers")),
        // CD-XPC-11: the XP capability on the outputs of the Experience Collector.
        OptionalMod("experiencelib", listOf("es/degrassi/experiencelib/"), listOf("collector_xp")),
        OptionalMod("dynamictrees", listOf("com/dtteam/dynamictrees/"), listOf("lumberjack_fell")),
        OptionalMod("apothic_enchanting", listOf("dev/shadowsoffire/"), compat = "apothic"),
        // CD-CRY-05: only compat/ae2/ may name AE2, for the AE2 Crank's ICrankable API (R83).
        OptionalMod("ae2", listOf("appeng/"), listOf("ae2_crank")),
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
        // The mods of the Crystal Tender that its tags alone reach (CD-MCH-02).
        OptionalMod("quark", listOf("org/violetmoon/quark/"), compat = null),
        OptionalMod("unusualend", listOf("net/sweety/unusualend/"), compat = null),
    )
}
