package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.registry.DynamicsBlocks
import codes.honor.cobblemondynamics.xp.CollectorMenu
import codes.honor.cobblemondynamics.xp.CollectorSlots
import codes.honor.cobblemondynamics.xp.ExperienceCollectorBlock
import codes.honor.cobblemondynamics.xp.ExperienceCollectorBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.AABB
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/**
 * The Experience Collector's button with real players and vanilla's packet handler, which checks the open menu, the spectator mode
 * and the reach of [CollectorMenu.stillValid] before it calls the button (RES13). A fake player's connection drops every packet, so
 * the players of the button are plain ones. Then the drops of collectors that a real explosion or a creative break breaks.
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

    /** CD-XPC-02: a player who faces north places a collector from its item, and the collector's front faces south, toward the player. */
    @GameTest(template = "floor_9x5x9")
    fun cdXpc02APlacedCollectorFacesThePlayer(helper: GameTestHelper) {
        val player = TestKit.owner(helper)
        player.yRot = 180f
        helper.assertTrue(player.direction == Direction.NORTH, "the player faces ${player.direction}, not north")
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(DynamicsBlocks.EXPERIENCE_COLLECTOR_ITEM.get()))
        val rel = BlockPos(4, 2, 4)
        TestKit.clickBlock(helper, player, rel.below())
        helper.assertBlockPresent(DynamicsBlocks.EXPERIENCE_COLLECTOR.get(), rel)
        val front = helper.getBlockState(rel).getValue(ExperienceCollectorBlock.FACING)
        helper.assertTrue(front == Direction.SOUTH, "CD-XPC-02: the collector of a player who faces north faces $front")
        helper.succeed()
    }

    /**
     * CD-XPC-15: a right-click on a collector opens its menu. Then [ProtectedBlocks]' listener cancels every right-click on the collector,
     * as a protection mod does, and the next right-click opens no menu.
     */
    @GameTest(template = "floor_9x5x9")
    fun cdXpc15ACancelledRightClickOpensNoMenu(helper: GameTestHelper) {
        val rel = BlockPos(4, 2, 4)
        helper.setBlock(rel, DynamicsBlocks.EXPERIENCE_COLLECTOR.get())
        val player = TestKit.menuCountingOwner(helper)
        TestKit.clickBlock(helper, player, rel, Direction.EAST)
        helper.assertTrue(player.menusOpened == 1, "a right-click opened ${player.menusOpened} menus")
        val pos = helper.absolutePos(rel)
        ProtectedBlocks.cancelled += pos
        TestKit.afterTest(helper) { ProtectedBlocks.cancelled -= pos }
        TestKit.clickBlock(helper, player, rel, Direction.EAST)
        helper.assertTrue(player.menusOpened == 1, "CD-XPC-15: a cancelled right-click opened a menu")
        helper.succeed()
    }

    /** The XP of each collector item in [drops]. */
    private fun storedXp(drops: List<ItemStack>): List<Int?> =
        drops.filter { it.`is`(DynamicsBlocks.EXPERIENCE_COLLECTOR_ITEM.get()) }.map { it.get(DynamicsBlocks.STORED_EXPERIENCE.get()) }

    /**
     * CD-XPC-13: an explosion of radius 3 with drop decay, as a creeper's, breaks a full collector, four times, and each drops one item
     * with all its XP. The loot condition `minecraft:survives_explosion` that the table had kept a drop 1 time in 3, so four drops in a
     * row came by chance once in 81 runs (R86).
     */
    @GameTest(template = "floor_9x5x9")
    fun cdXpc13AnExplodedCollectorDropsItselfWithItsXp(helper: GameTestHelper) {
        val capacity = WorkEngineSettings.current.experienceCollectorCapacity
        for (rel in listOf(BlockPos(2, 2, 2), BlockPos(6, 2, 2), BlockPos(2, 2, 6), BlockPos(6, 2, 6))) {
            helper.setBlock(rel, DynamicsBlocks.EXPERIENCE_COLLECTOR.get())
            val collector = helper.getBlockEntity<ExperienceCollectorBlockEntity>(rel)
            helper.assertTrue(collector.insert(capacity) == capacity, "the collector refused $capacity XP")
            val drops = Explosions.creeperAt(helper, rel)
            helper.assertBlockNotPresent(DynamicsBlocks.EXPERIENCE_COLLECTOR.get(), rel)
            helper.assertTrue(storedXp(drops) == listOf(capacity), "CD-XPC-13: the exploded collector at $rel dropped $drops")
        }
        helper.succeed()
    }

    /**
     * CD-XPC-13: a player in creative mode breaks a collector with 12,345 XP and gets its item with the XP, as from a shulker box that
     * holds items, and breaks an empty collector and gets nothing (R86).
     */
    @GameTest(template = "floor_9x5x9")
    fun cdXpc13ACreativeBreakDropsACollectorThatHoldsXp(helper: GameTestHelper) {
        val full = BlockPos(2, 2, 4)
        val empty = BlockPos(6, 2, 4)
        for (rel in listOf(full, empty)) helper.setBlock(rel, DynamicsBlocks.EXPERIENCE_COLLECTOR.get())
        helper.assertTrue(helper.getBlockEntity<ExperienceCollectorBlockEntity>(full).insert(12_345) == 12_345, "the collector refused XP")
        val player = TestKit.owner(helper)
        player.setGameMode(GameType.CREATIVE)
        helper.assertTrue(player.isCreative, "the player is not in creative mode")

        fun breakAt(rel: BlockPos): List<ItemStack> {
            val pos = helper.absolutePos(rel)
            helper.assertTrue(player.gameMode.destroyBlock(pos), "the player could not break the collector at $rel")
            helper.assertBlockNotPresent(DynamicsBlocks.EXPERIENCE_COLLECTOR.get(), rel)
            val drops = helper.level.getEntitiesOfClass(ItemEntity::class.java, AABB(pos).inflate(2.0))
            val stacks = drops.map { it.item.copy() }
            for (drop in drops) drop.discard()
            return stacks
        }
        val fromFull = breakAt(full)
        helper.assertTrue(storedXp(fromFull) == listOf(12_345), "CD-XPC-13: the creative break of 12,345 XP dropped $fromFull")
        val fromEmpty = breakAt(empty)
        helper.assertTrue(fromEmpty.isEmpty(), "CD-XPC-13: the creative break of an empty collector dropped $fromEmpty")
        helper.succeed()
    }
}
