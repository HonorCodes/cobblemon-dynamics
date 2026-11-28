package codes.honor.cobblemondynamics.engine

import net.minecraft.core.BlockPos

/** Pre-claim and active-claim break gate for roles whose work breaks or replaces target cells (CD-TEAM-08). */
object BreakingClaimGate {
    fun mayClaim(breaksBlocks: Boolean, mayUse: Boolean, mayBreak: () -> Boolean): Boolean = mayUse && (!breaksBlocks || mayBreak())

    fun mayClaimCells(mayUse: Boolean, breakTargets: List<BlockPos>, mayBreakCell: (BlockPos) -> Boolean): Boolean =
        mayUse && breakTargets.all(mayBreakCell)
}
