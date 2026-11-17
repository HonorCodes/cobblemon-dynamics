package codes.honor.cobblemondynamics.compat.cobblemon_incubator

/** The pure rules of the Stoker on the Egg Incubator of Cobblemon Incubator 1.1.1 (CD-FUEL-11, CD-FUEL-14). */
object IncubatorRule {
    /**
     * CD-FUEL-11: the speed of an incubator this tick, from its own speed [own]. A worked incubator runs at the speed of one Speed Upgrade,
     * [upgraded], and one that has the upgrade gets nothing more: warmth is no fuel, so the worker gives at most one upgrade. A server that
     * turns Speed Upgrades off gets none from a worker either (R88). The speed lives only in the ticks that a worker works, so the
     * incubator's own speed returns at once (CD-FUEL-14).
     */
    fun speed(own: Int, upgraded: Int, upgradesEnabled: Boolean, worked: Boolean): Int =
        if (worked && upgradesEnabled) maxOf(own, upgraded) else own

    /**
     * CD-FUEL-11: an incubator gains from a worker while it holds an egg, Speed Upgrades work on the server, it has no Speed Upgrade of its
     * own, and its own tick [incubates] that egg. The last check reads the egg, so it runs only when the others pass.
     */
    fun hasDemand(hasEgg: Boolean, upgradesEnabled: Boolean, hasSpeedUpgrade: Boolean, incubates: () -> Boolean): Boolean =
        hasEgg && upgradesEnabled && !hasSpeedUpgrade && incubates()

    /**
     * The incubator's own tick (`tickServer`, 1.1.1) counts an egg down only when the egg passes its filter, or the config ignores the
     * filter on a [shiny] egg, and no Analyse Upgrade holds the egg for its analysis ([analysing]).
     */
    fun incubates(analysing: Boolean, passesFilter: Boolean, shiny: Boolean, ignoreFilterOnShiny: Boolean): Boolean =
        !analysing && (passesFilter || (ignoreFilterOnShiny && shiny))

    /**
     * The incubator's private `passesFilter`: an incubator whose Filter Upgrade has no [criteria], or that has none, passes every egg, and
     * one with criteria passes an egg whose properties it [matches]. An egg with no properties gives null and fails.
     */
    fun passesFilter(criteria: Boolean, matches: Boolean?): Boolean = !criteria || matches == true
}
