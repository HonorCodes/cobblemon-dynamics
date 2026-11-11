package codes.honor.cobblemondynamics.compat.minecolonies

import codes.honor.cobblemondynamics.compat.LandClaimCheck
import codes.honor.cobblemondynamics.compat.LandClaims
import com.minecolonies.api.IMinecoloniesAPI
import com.minecolonies.api.blocks.AbstractBlockHut
import com.minecolonies.api.colony.IColony
import com.minecolonies.api.colony.IColonyManager
import com.minecolonies.api.colony.permissions.Action
import com.minecolonies.api.colony.permissions.IPermissions
import com.minecolonies.api.items.ModTags
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.capabilities.Capabilities
import java.util.UUID

/**
 * The MineColonies checks of Quick Work (CD-LND-09) and of pastures (CD-LND-08): a colony's own ranks decide who may use a block. Every
 * class that names MineColonies or a library of LDTTeam sits in this package, and only [register] is called from outside, behind
 * `ModList.isLoaded("minecolonies")` (CD-LND-13).
 */
object MineColoniesCompat {
    fun register() = LandClaims.register("minecolonies", Check)

    private object Check : LandClaimCheck {
        /** The owner's own click, with the operator bypass of `hasPermission(Player, Action)` (CD-LND-09). */
        override fun mayUse(owner: ServerPlayer, level: ServerLevel, pos: BlockPos): Boolean =
            allows(level, pos, { it.getRank(owner).isHostile }) { permissions, action -> permissions.hasPermission(owner, action) }

        /**
         * The rank of the placer by UUID, online or not, with no bypass, which needs a player in creative mode at the click (CD-LND-08). A
         * pasture with no placer gets no block of a colony. It posts no event, sends no message and counts no attempt (CD-LND-10).
         */
        override fun ownerMayUse(ownerId: UUID?, level: ServerLevel, pos: BlockPos): Boolean {
            if (ownerId == null) return level.isLoaded(pos) && colonyAt(level, pos) == null
            return allows(level, pos, { it.getRank(ownerId).isHostile }) { permissions, action ->
                permissions.hasPermission(permissions.getRank(ownerId), action)
            }
        }
    }

    private val ACTIONS = mapOf(
        ColonyRightClick.Need.ACCESS_HUTS to Action.ACCESS_HUTS,
        ColonyRightClick.Need.ACCESS_TOGGLEABLES to Action.ACCESS_TOGGLEABLES,
        ColonyRightClick.Need.RIGHTCLICK_BLOCK to Action.RIGHTCLICK_BLOCK,
        ColonyRightClick.Need.OPEN_CONTAINER to Action.OPEN_CONTAINER,
    )

    /** The colony whose land holds [pos], as its event handler decides. `getColonyByPosFromWorld` loads the chunk, so callers ask isLoaded first. */
    private fun colonyAt(level: ServerLevel, pos: BlockPos): IColony? =
        IColonyManager.getInstance().getColonyByPosFromWorld(level, pos)?.takeIf { it.isCoordInColony(level, pos) }

    private fun allows(
        level: ServerLevel,
        pos: BlockPos,
        hostile: (IPermissions) -> Boolean,
        has: (IPermissions, Action) -> Boolean,
    ): Boolean {
        if (!level.isLoaded(pos)) return false
        val colony = colonyAt(level, pos) ?: return true
        val permissions = colony.permissions
        val state = level.getBlockState(pos)
        val block = ColonyRightClick.Block(
            hut = state.block is AbstractBlockHut<*>,
            exception = state.`is`(ModTags.colonyProtectionException),
            toggleable = state.`is`(BlockTags.DOORS) || state.`is`(BlockTags.FENCE_GATES),
            container = hasItemHandler(level, pos, state),
        )
        val protection = IMinecoloniesAPI.getInstance().config.server.enableColonyProtection.get()
        return ColonyRightClick.allows(block, protection, hostile(permissions)) { has(permissions, ACTIONS.getValue(it)) }
    }

    /** As the event handler asks: with no side, then with each side. */
    private fun hasItemHandler(level: ServerLevel, pos: BlockPos, state: BlockState): Boolean {
        val blockEntity = level.getBlockEntity(pos)
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, state, blockEntity, null) != null ||
            Direction.entries.any { level.getCapability(Capabilities.ItemHandler.BLOCK, pos, state, blockEntity, it) != null }
    }
}
