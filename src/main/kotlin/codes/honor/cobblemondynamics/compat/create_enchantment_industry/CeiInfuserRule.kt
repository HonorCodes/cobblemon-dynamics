package codes.honor.cobblemondynamics.compat.create_enchantment_industry

import net.minecraft.core.BlockPos

/**
 * The Muse's rules at the Infuser of Create: Enchantment Industry, over positions and plain facts, so that unit tests load them with no
 * class of CEI or Create (CD-MCH-06). CEI 2.5.4b gathers the Infuser's table stats one block below it, only while the Infuser is idle and
 * once every 200 ticks, and an infusion keeps the stats that it started with (javap).
 */
object CeiInfuserRule {
    /** `InfuserBlockEntity.updateInfusionStats` gathers at `getBlockPos().below()`. */
    const val STATS_BELOW = 1

    /** `InfuserBlockEntity.getBasin` reads the block entity at `below(2)`, and the Infuser cannot stand on the basin itself. */
    const val BASIN_BELOW = 2

    fun statsPos(infuser: BlockPos): BlockPos = infuser.below(STATS_BELOW)

    fun basinPos(infuser: BlockPos): BlockPos = infuser.below(BASIN_BELOW)

    /**
     * CD-MCH-06: the Infuser stands over its basin. CEI reads the stats only while the Infuser is idle, so the Muse works the assembled
     * Infuser whether or not an infusion runs, and the next infusion starts with its Quanta.
     */
    fun hasDemand(basinBelow: Boolean): Boolean = basinBelow
}
