package codes.honor.cobblemondynamics.role

import codes.honor.cobblemondynamics.MixinState
import codes.honor.cobblemondynamics.id
import net.minecraft.resources.ResourceLocation

/** Turns off the roles whose handler needs a mixin that did not inject, so a changed Create or NeoForge never crashes the game. */
object MixinGate {
    /** Role handler id -> the mixin it needs. */
    val REQUIRED: Map<ResourceLocation, String> = mapOf(
        id("water_wheel") to MixinState.WATER_WHEEL,
        id("burner_heat") to MixinState.BLAZE_BURNER,
        id("enchanting") to MixinState.ENCHANTMENT_MENU,
        id("fan_catalyst") to MixinState.AIR_CURRENT,
        id("tailwind") to MixinState.WINDMILL_BEARING,
        id("ie_watermill") to MixinState.IE_WATERMILL,
        id("ie_windmill") to MixinState.IE_WINDMILL,
        id("wind_generator") to MixinState.WIND_GENERATOR,
        // CD-OPT-09: the Stoker's handlers of other mods that work through a mixin of their own (CD-FUEL-10, CD-FUEL-11).
        id("clibano") to MixinState.CLIBANO,
        id("egg_incubator") to MixinState.EGG_INCUBATOR,
        // CD-MCH-01: the Superheater on the Liquid Blaze Burner of Create Crafts & Additions.
        id("liquid_burner") to MixinState.LIQUID_BLAZE_BURNER,
    )

    /** CD-MUS-07: with Apothic Enchanting, the Muse needs the stats hook, because that mod's table menu never runs the menu mixin. */
    fun required(apothicEnchanting: Boolean): Map<ResourceLocation, String> =
        if (apothicEnchanting) REQUIRED + (id("enchanting") to MixinState.APOTHIC_TABLE_STATS) else REQUIRED

    /**
     * CD-OPT-09: each target inside a shared handler that needs a mixin, by target key, with that mixin, such as a station of another
     * mod for the Muse. A missing mixin turns off that target only: the handler asks `RoleRegistry.targetOn`. The tasks of 1.2 that
     * add such a mixin add its target.
     */
    val REQUIRED_FOR_TARGET: Map<String, String> = mapOf(
        "easymagic:enchanting_table" to MixinState.EASY_MAGIC_MENU,
        "enchantinginfuser:enchanting_infuser" to MixinState.INFUSER_MENU,
        "enchantinginfuser:advanced_enchanting_infuser" to MixinState.INFUSER_MENU,
    )

    /**
     * CD-OPT-09: the targets of [required] whose mod is [loaded] and whose mixin did not apply, with one warning for each, in target
     * order. A target names its block, whose namespace is its mod: without the mod, the block and its mixin do not exist.
     */
    fun offTargets(
        required: Map<String, String>,
        applied: (String) -> Boolean,
        loaded: (String) -> Boolean,
        warn: (String) -> Unit,
    ): Set<String> {
        val off = required.filter { (target, mixin) -> loaded(target.substringBefore(':')) && !applied(mixin) }.toSortedMap()
        for ((target, mixin) in off) warn("Target $target is disabled: its mixin $mixin did not apply")
        return off.keys
    }

    fun apply(roles: RoleSet, applied: (String) -> Boolean, warn: (String) -> Unit): RoleSet = apply(roles, applied, REQUIRED, warn)

    /** Disables each role whose mixin is missing, with one warning per handler (CD-WHL-10, CD-BRN-07, CD-MUS-06, CD-FAN-17, CD-TLW-10). */
    fun apply(roles: RoleSet, applied: (String) -> Boolean, required: Map<ResourceLocation, String>, warn: (String) -> Unit): RoleSet {
        val missing = roles.active.map { it.definition.handler }.distinct().mapNotNull { handler ->
            required[handler]?.takeUnless(applied)?.let { handler to it }
        }
        if (missing.isEmpty()) return roles
        for ((handler, mixin) in missing) warn("Handler $handler is disabled: its mixin $mixin did not apply")
        val mixinOf = missing.toMap()
        return roles.withoutHandlers(mixinOf.keys) { handler -> "its mixin ${mixinOf[handler]} did not apply" }
    }
}
