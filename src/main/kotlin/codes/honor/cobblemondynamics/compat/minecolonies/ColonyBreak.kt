package codes.honor.cobblemondynamics.compat.minecolonies

/**
 * The break rule of MineColonies 1.1.1420 for a block that is no hut and no decoration controller, as RES8 read it from
 * `ColonyPermissionEventHandler.on(BlockEvent.BreakEvent)` (CD-HRV-19). Names no MineColonies class, so a unit test reads it without
 * MineColonies.
 */
object ColonyBreak {
    /** [protection] is `enableColonyProtection`, [inColony] whether a colony holds the block, and [rankBreaks] the rank's `BREAK_BLOCKS`. */
    data class Facts(val protection: Boolean, val inColony: Boolean, val rankBreaks: Boolean)

    /** A colony refuses a break only while it protects its blocks, holds this one, and the rank lacks `BREAK_BLOCKS`. */
    fun allows(f: Facts): Boolean = !f.protection || !f.inColony || f.rankBreaks
}
