package codes.honor.cobblemondynamics.compat.cobblemon_incubator

/** The pure rules of the Stoker on the Egg Incubator of Cobblemon Incubator 1.1.1 (CD-FUEL-11, CD-FUEL-14). */
object IncubatorRule {
    /**
     * CD-FUEL-11: the speed of an incubator this tick, from its own speed [own]. A worked incubator runs at the speed of one Speed Upgrade,
     * [upgraded], and one that has the upgrade gets nothing more. Warmth is no fuel, so the worker gives at most one upgrade, and a server
     * that turns Speed Upgrades off gets none. The speed lives only in the ticks that a worker works, so the incubator's own speed returns
     * at once (CD-FUEL-14).
     */
    fun speed(own: Int, upgraded: Int, upgradesEnabled: Boolean, worked: Boolean): Int =
        if (worked && upgradesEnabled) maxOf(own, upgraded) else own

    /** An incubator gains from a worker while it holds an egg, Speed Upgrades work on the server, and it has no Speed Upgrade of its own. */
    fun hasDemand(hasEgg: Boolean, upgradesEnabled: Boolean, hasSpeedUpgrade: Boolean): Boolean =
        hasEgg && upgradesEnabled && !hasSpeedUpgrade
}
