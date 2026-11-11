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
    )

    /** Disables each role whose mixin is missing, with one warning per handler (CD-WHL-10, CD-BRN-07, CD-MUS-06, CD-FAN-17, CD-TLW-10). */
    fun apply(roles: RoleSet, applied: (String) -> Boolean, warn: (String) -> Unit): RoleSet {
        val missing = roles.active.map { it.definition.handler }.distinct().mapNotNull { handler ->
            REQUIRED[handler]?.takeUnless(applied)?.let { handler to it }
        }
        if (missing.isEmpty()) return roles
        for ((handler, mixin) in missing) warn("Handler $handler is disabled: its mixin $mixin did not apply")
        val mixinOf = missing.toMap()
        return roles.withoutHandlers(mixinOf.keys) { handler -> "its mixin ${mixinOf[handler]} did not apply" }
    }
}
