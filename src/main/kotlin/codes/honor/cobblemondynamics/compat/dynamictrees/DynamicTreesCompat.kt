package codes.honor.cobblemondynamics.compat.dynamictrees

import codes.honor.cobblemondynamics.compat.AdapterNeeds
import codes.honor.cobblemondynamics.compat.CompatLines
import codes.honor.cobblemondynamics.handler.tree.LumberjackHandler
import com.dtteam.dynamictrees.block.branch.BranchBlock
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity

/**
 * Dynamic Trees fells its own trees (ADR-0017, decision 15). A branch that loses its block with no entity drops nothing by default, so
 * the Lumberjack hands a tree whose base log is in `#dynamictrees:branches` to `BranchBlock.futureBreak`, with the worker as the
 * entity, which fells the tree as for a player's axe (CD-LUM-25). The mod calls [register] only while Dynamic Trees is loaded.
 */
object DynamicTreesCompat {
    /** A branch of Dynamic Trees 1.7.2, so that the compat line names a renamed branch as missing. */
    private val OAK_BRANCH: ResourceLocation = ResourceLocation.fromNamespaceAndPath("dynamictrees", "oak_branch")

    fun register() {
        LumberjackHandler.dynamicTrees = LumberjackHandler.ModFell(::fell)
        CompatLines.register("dynamictrees", "lumberjack_fell", AdapterNeeds(blocks = listOf(OAK_BRANCH)))
    }

    /** Fells the tree above [base], with [base], for [worker], or returns false when [base] holds no branch. */
    private fun fell(level: ServerLevel, base: BlockPos, worker: LivingEntity): Boolean {
        val state = level.getBlockState(base)
        val branch = state.block as? BranchBlock ?: return false
        branch.futureBreak(state, level, base, worker)
        return true
    }
}
