package codes.honor.cobblemondynamics.compat.dynamictrees

import codes.honor.cobblemondynamics.compat.AdapterNeeds
import codes.honor.cobblemondynamics.compat.CompatLines
import codes.honor.cobblemondynamics.handler.tree.LumberjackHandler
import com.dtteam.dynamictrees.block.branch.BranchBlock
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * Dynamic Trees fells its own trees (ADR-0017, decision 15). A branch that loses its block with no entity drops nothing by default, so
 * the Lumberjack hands a tree of Dynamic Trees to `BranchBlock.futureBreak`, with the worker as the entity, which fells the tree as for
 * a player's axe (CD-LUM-25). The hook also reports a `BranchBlock` of any namespace, whatever its tags (R89). The mod calls [register]
 * only while Dynamic Trees is loaded.
 */
object DynamicTreesCompat {
    /** A branch of Dynamic Trees 1.7.2, so that the compat line names a renamed branch as missing. */
    private val OAK_BRANCH: ResourceLocation = ResourceLocation.fromNamespaceAndPath("dynamictrees", "oak_branch")

    fun register() {
        LumberjackHandler.dynamicTrees = Hook
        CompatLines.register("dynamictrees", "lumberjack_fell", AdapterNeeds(blocks = listOf(OAK_BRANCH)))
    }

    /** Dynamic Trees' own fell, and its own test of a branch. */
    private object Hook : LumberjackHandler.ModFell {
        /** Fells the tree above [base], with [base], for [worker], or returns false when [base] holds no branch. */
        override fun fell(level: ServerLevel, base: BlockPos, worker: LivingEntity): Boolean {
            val state = level.getBlockState(base)
            val branch = state.block as? BranchBlock ?: return false
            branch.futureBreak(state, level, base, worker)
            return true
        }

        override fun isBranch(state: BlockState): Boolean = state.block is BranchBlock
    }
}
