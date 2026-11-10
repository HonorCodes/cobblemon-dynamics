package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.net.DynamicsPayloads
import codes.honor.cobblemondynamics.net.PanelViewers
import codes.honor.cobblemondynamics.net.WorkModeTogglePayload
import codes.honor.cobblemondynamics.pasture.PastureAdapter
import com.cobblemon.mod.common.api.pasture.PastureLinkManager
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/**
 * The Work Mode payloads through their server handlers, as the panel's button sends them. A player opens the panel as in the game: an
 * empty-hand click on the pasture, on which Cobblemon links the player to it, and then the panel's open payload.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class PayloadTests {
    private val pastureRel = BlockPos(4, 1, 4)

    private fun openPanel(helper: GameTestHelper, player: ServerPlayer) {
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
        TestKit.clickBlock(helper, player, pastureRel)
        val linked = PastureAdapter.linkedPasture(player)
        helper.assertTrue(linked == helper.absolutePos(pastureRel), "Cobblemon linked ${player.gameProfile.name} to $linked")
        DynamicsPayloads.onPanelOpen(player)
        TestKit.afterTest(helper) {
            PastureLinkManager.links.remove(player.uuid)
            PanelViewers.forget(player)
        }
    }

    private fun toggle(player: ServerPlayer, enabled: Boolean) = DynamicsPayloads.onToggle(player, WorkModeTogglePayload(enabled))

    /**
     * The placer's on payload turns Work Mode on within 1 tick (CD-WMD-03). The same payload 10 ticks later leaves it on, and the panel
     * shows on (CD-SEC-05). The placer's off payload then turns it off within 1 tick (CD-WMD-03).
     */
    @GameTest(template = "floor_9x5x9", timeoutTicks = 100)
    fun cdSec05Wmd03PlacerPayloadsSetTheRequestedState(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, pastureRel, owner)
        TestKit.standBeside(owner, pasture)
        openPanel(helper, owner)
        toggle(owner, true)
        helper.runAfterDelay(1) {
            helper.assertTrue(PastureAdapter.isWorkMode(pasture), "CD-WMD-03: the placer's on payload left Work Mode off")
        }
        helper.runAfterDelay(10) { toggle(owner, true) }
        helper.runAfterDelay(11) {
            helper.assertTrue(PastureAdapter.isWorkMode(pasture), "CD-SEC-05: the repeated on payload turned Work Mode off")
            val shown = PanelViewers.lastSent(owner.uuid)
            helper.assertTrue(shown?.workMode == true, "CD-SEC-05: the placer's panel shows $shown after the repeated on payload")
        }
        helper.runAfterDelay(22) { toggle(owner, false) }
        helper.runAfterDelay(23) {
            helper.assertFalse(PastureAdapter.isWorkMode(pasture), "CD-WMD-03: the placer's off payload left Work Mode on")
            helper.succeed()
        }
    }

    /**
     * Two operators who did not place the pasture have its panel open. The first one's on payload turns Work Mode on (CD-WMD-04), and both
     * panels show on. The second one's off payload in the same tick wins: Work Mode is off, and both panels show off (CD-SEC-06).
     */
    @GameTest(template = "floor_9x5x9", timeoutTicks = 100)
    fun cdSec06Wmd04TwoOperatorsInOneTick(helper: GameTestHelper) {
        val pasture = TestKit.placePasture(helper, pastureRel, TestKit.owner(helper))
        val viewers = listOf(TestKit.operator(helper, "op_a"), TestKit.operator(helper, "op_b"))
        for (viewer in viewers) {
            TestKit.standBeside(viewer, pasture)
            openPanel(helper, viewer)
        }
        val (first, second) = viewers
        toggle(first, true)
        helper.assertTrue(PastureAdapter.isWorkMode(pasture), "CD-WMD-04: an operator's on payload left Work Mode off")
        for (viewer in viewers) {
            val shown = PanelViewers.lastSent(viewer.uuid)
            helper.assertTrue(shown?.workMode == true, "the panel of ${viewer.gameProfile.name} shows $shown after the on payload")
        }
        toggle(second, false)
        helper.runAfterDelay(1) {
            helper.assertFalse(PastureAdapter.isWorkMode(pasture), "CD-SEC-06: on, then off, in one tick left Work Mode on")
            for (viewer in viewers) {
                val shown = PanelViewers.lastSent(viewer.uuid)
                helper.assertTrue(shown?.workMode == false, "CD-SEC-06: the panel of ${viewer.gameProfile.name} shows $shown")
            }
            helper.succeed()
        }
    }
}
