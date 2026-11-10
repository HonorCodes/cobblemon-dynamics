package codes.honor.cobblemondynamics.client

import codes.honor.cobblemondynamics.MixinState

/**
 * Checks the client mixins of the pasture panel. A mixin that did not apply turns off only its own part of the panel, with one
 * warning per mixin and game session, and the Work Mode button and the head bars keep working without it (CD-UI-13).
 */
class PanelMixinCheck(private val applied: (String) -> Boolean, private val warn: (String) -> Unit) {
    private val warned = HashSet<String>()

    /** False when no row shows its Work Stamina bar and state icon. */
    fun barsOn(): Boolean = check(MixinState.PASTURE_WIDGET, "the pasture panel shows no Work Stamina bars")

    /** False when the list keeps Cobblemon's height and reaches under the Work Mode button. */
    fun listShortened(): Boolean = check(MixinState.PASTURE_LIST, "the pasture list reaches under the Work Mode button")

    private fun check(key: String, effect: String): Boolean {
        val ok = applied(key)
        if (!ok && warned.add(key)) warn("Mixin $key did not apply, so $effect")
        return ok
    }
}
