package codes.honor.cobblemondynamics.compat

import codes.honor.cobblemondynamics.MixinState

/**
 * Checks once per game that the Cobblebase hooks applied. A Cobblebase version that lacks a hooked class or method leaves that hook
 * out, so the mod logs one warning that names the missing hooks and runs on (CD-CMP-05). The mod loads it only with Cobblebase.
 */
object CobblebaseHooks {
    /** The handler names, after `cobblemon_dynamics$`, of the hooks in each Cobblebase class. */
    val HOOKS: Map<String, List<String>> = mapOf(
        "notlown.cobblebase.core.AmbientBehavior" to listOf("sleepLock", "restLock", "idle", "animations"),
        "notlown.cobblebase.core.NavigationHelper" to listOf("waterTeleport", "wander", "unstick", "leaves"),
        "notlown.cobblebase.core.BaseManager" to listOf("jobs"),
        "notlown.cobblebase.core.PassiveXp" to listOf("passiveXp"),
    )

    /**
     * The hooks that did not apply. [load] loads a class by name, which makes Mixin apply the hooks to it, and is false when the
     * class does not exist. [applied] tells whether a handler was injected.
     */
    fun missing(load: (String) -> Boolean, applied: (String) -> Boolean): List<String> =
        HOOKS.flatMap { (target, handlers) -> if (load(target)) handlers.filterNot(applied) else handlers }

    fun check(warn: (String) -> Unit) {
        val missing = missing(::load, MixinState::handlerApplied)
        if (missing.isEmpty()) return
        warn("Cobblebase hooks $missing did not apply, so Cobblebase can still move or freeze the workers of Work Mode pastures")
    }

    /** Loads without initializing, so no Cobblebase code runs here. A class that cannot load counts as missing. */
    private fun load(name: String): Boolean = try {
        Class.forName(name, false, CobblebaseHooks::class.java.classLoader)
        true
    } catch (e: ClassNotFoundException) {
        false
    } catch (e: LinkageError) {
        false
    }
}
