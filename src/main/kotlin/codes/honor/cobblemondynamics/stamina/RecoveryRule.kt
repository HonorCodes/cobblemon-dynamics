package codes.honor.cobblemondynamics.stamina

/** How a Pokémon recovers Work Stamina. A Fairy-type with no machine binding is a healer, and every other Pokémon a worker. */
enum class RecoveryRule {
    WORKER,
    HEALER,
    ;

    companion object {
        /** CD-HEAL-13: a leashed Fairy-type, for example a Muse, follows the worker rule. */
        fun of(hasFairyType: Boolean, hasMachineBinding: Boolean): RecoveryRule = if (hasFairyType && !hasMachineBinding) HEALER else WORKER
    }
}
