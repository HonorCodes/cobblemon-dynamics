package codes.honor.cobblemondynamics.handler.farming

/**
 * CD-CRP-09: whether a binding of a farming role keeps its crop's cell. A player who harvests the crop by hand leaves air above the
 * farmland, and the leashed worker waits there for the replant. A block in the cell, or nothing under it, ends the binding.
 */
object PlotRule {
    /** [cellInRoleTag]: the cell holds a crop that the role works. [cellEmpty]: it holds air or a fluid. */
    fun keeps(cellInRoleTag: Boolean, cellEmpty: Boolean, belowIsAir: Boolean): Boolean = cellInRoleTag || (cellEmpty && !belowIsAir)
}
