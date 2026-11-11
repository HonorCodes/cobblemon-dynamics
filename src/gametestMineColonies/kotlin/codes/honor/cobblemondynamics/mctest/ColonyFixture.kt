package codes.honor.cobblemondynamics.mctest

import codes.honor.cobblemondynamics.test.TestKit
import com.minecolonies.api.colony.IColony
import com.minecolonies.api.colony.IColonyManager
import com.minecolonies.api.colony.permissions.IPermissions
import com.minecolonies.api.colony.permissions.Rank
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent

/** Colonies, ranks and the colony's own decision on a click, for the test players of the MineColonies run. */
object ColonyFixture {
    /** The center of each test's colony, which claims the chunks around it at once. */
    val CENTER = BlockPos(11, 2, 11)

    /**
     * A colony of [owner] around the test's [CENTER], which builds nothing, so any structure pack name does. MineColonies deletes it,
     * with no block change, when the test ends.
     */
    fun colony(helper: GameTestHelper, owner: ServerPlayer): IColony {
        val manager = IColonyManager.getInstance()
        val name = "cdtest_${owner.uuid.toString().take(8)}"
        val colony = manager.createColony(helper.level, helper.absolutePos(CENTER), owner, name, "cdtest")
            ?: throw GameTestAssertException("MineColonies made no colony for ${owner.gameProfile.name}")
        TestKit.afterTest(helper) { manager.deleteColonyByWorld(colony.id, false, helper.level) }
        return colony
    }

    /** Gives [player] the rank that [rank] picks from the colony's ranks. */
    fun rank(colony: IColony, player: ServerPlayer, rank: (IPermissions) -> Rank) {
        val permissions = colony.permissions
        if (!permissions.addPlayer(player.gameProfile, rank(permissions))) {
            throw GameTestAssertException("MineColonies did not add ${player.gameProfile.name} to the colony")
        }
    }

    /** MineColonies' own decision on a right-click of [player] with an empty main hand on [pos]: the event that a test posts is not canceled. */
    fun colonyAllows(player: ServerPlayer, pos: BlockPos): Boolean {
        val hit = BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)
        return !NeoForge.EVENT_BUS.post(PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit)).isCanceled
    }
}
