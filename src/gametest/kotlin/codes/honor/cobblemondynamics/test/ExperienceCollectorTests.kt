package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.registry.DynamicsBlocks
import codes.honor.cobblemondynamics.xp.CollectorMenu
import codes.honor.cobblemondynamics.xp.CollectorSlots
import codes.honor.cobblemondynamics.xp.ExperienceCollectorBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/**
 * The Experience Collector's button with real players and vanilla's packet handler, which checks the open menu, the spectator mode
 * and the reach of [CollectorMenu.stillValid] before it calls the button (RES13). A fake player's connection drops every packet, so
 * the players here are plain ones.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class ExperienceCollectorTests {
    /** "Collect experience" as the client sends it: vanilla's container button packet for the open menu. */
    private fun press(player: ServerPlayer) = player.connection.handleContainerButtonClick(
        ServerboundContainerButtonClickPacket(player.containerMenu.containerId, CollectorSlots.COLLECT_BUTTON),
    )

    /**
     * CD-XPC-04, CD-XPC-05: a collector with 1,234 XP that a player beside it and a player 9 blocks away open through its use path.
     * The far player presses the button and gets nothing, because the menu is out of reach. The near player then gets all 1,234 XP,
     * the store holds none, and a second press gives nothing.
     */
    @GameTest(template = "floor_15x6x15")
    fun cdXpc05OnlyAPlayerInReachCollects(helper: GameTestHelper) {
        val rel = BlockPos(3, 2, 7)
        helper.setBlock(rel, DynamicsBlocks.EXPERIENCE_COLLECTOR.get())
        val collector = helper.getBlockEntity<ExperienceCollectorBlockEntity>(rel)
        helper.assertTrue(collector.insert(1_234) == 1_234, "the collector refused 1,234 XP")
        val near = TestKit.plainOwner(helper, BlockPos(4, 2, 7), "near").player
        val far = TestKit.plainOwner(helper, BlockPos(12, 2, 7), "far").player
        for (player in listOf(near, far)) {
            TestKit.clickBlock(helper, player, rel, Direction.EAST)
            helper.assertTrue(player.containerMenu is CollectorMenu, "CD-XPC-04: a right-click opened ${player.containerMenu}")
        }
        press(far)
        helper.assertTrue(far.totalExperience == 0, "CD-XPC-05: the player 9 blocks away got ${far.totalExperience} XP")
        helper.assertTrue(collector.store.stored == 1_234, "CD-XPC-05: the far press left ${collector.store.stored} XP")
        press(near)
        helper.assertTrue(near.totalExperience == 1_234, "CD-XPC-05: the near player got ${near.totalExperience} XP")
        helper.assertTrue(collector.store.stored == 0, "CD-XPC-05: the store keeps ${collector.store.stored} XP")
        press(near)
        helper.assertTrue(near.totalExperience == 1_234, "CD-XPC-05: a second press gave ${near.totalExperience - 1_234} XP")
        helper.succeed()
    }
}
