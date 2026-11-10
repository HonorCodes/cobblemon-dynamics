package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.Feedback
import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.stamina.RecoveryRule
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.inventory.EnchantmentMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/**
 * The Muse on real enchanting tables. Fake players drop every menu that a block opens for them, so a test opens a menu as
 * `ServerPlayer.openMenu` does: it sets the player's `containerMenu` and posts NeoForge's `PlayerContainerEvent.Open`.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class MuseTests {
    private class Scene(val owner: ServerPlayer, val pasture: PokemonPastureBlockEntity, val muse: PokemonEntity)

    /** A Work Mode pasture at [pastureRel] with a Lv50 Clefairy that the Work Leash binds to the enchanting table at [tableRel]. */
    private fun scene(helper: GameTestHelper, pastureRel: BlockPos, tableRel: BlockPos): Scene {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, pastureRel, owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        helper.setBlock(tableRel, Blocks.ENCHANTING_TABLE)
        val clefairy = TestKit.tether(helper, pasture, owner, "clefairy level=50")
        val binding = TestKit.bind(helper, owner, clefairy, tableRel)
        val reply = Feedback.recorded[owner.uuid].orEmpty().lastOrNull()?.string
        helper.assertTrue(
            binding?.target == helper.absolutePos(tableRel) && binding.roleId == id("muse"),
            "CD-MUS-01: the bind to the enchanting table gave $binding and answered '$reply'",
        )
        return Scene(owner, pasture, clefairy)
    }

    private fun openMenu(helper: GameTestHelper, player: ServerPlayer, tableRel: BlockPos): EnchantmentMenu {
        val menu = EnchantmentMenu(1, player.inventory, ContainerLevelAccess.create(helper.level, helper.absolutePos(tableRel)))
        player.containerMenu = menu
        NeoForge.EVENT_BUS.post(PlayerContainerEvent.Open(player, menu))
        TestKit.afterTest(helper) { closeMenu(player, menu) }
        return menu
    }

    private fun closeMenu(player: ServerPlayer, menu: EnchantmentMenu) {
        if (player.containerMenu !== menu) return
        NeoForge.EVENT_BUS.post(PlayerContainerEvent.Close(player, menu))
        player.containerMenu = player.inventoryMenu
    }

    private fun offers(menu: EnchantmentMenu): List<Int> = menu.costs.toList() + menu.enchantClue.toList() + menu.levelClue.toList()

    private fun isStandbyOnTable(helper: GameTestHelper, entity: PokemonEntity, tableRel: BlockPos): Boolean {
        val claim = TestKit.claimOf(helper, entity)
        return claim?.kind == ClaimKind.WORK &&
            claim.key == TestKit.keyOf(helper, helper.absolutePos(tableRel)) &&
            TestKit.workerOf(helper, entity)?.status == WorkerStatus.STANDBY
    }

    /**
     * A Lv50 Muse adds ceil(15 × 50 ÷ 100) = 8 bookshelves (CD-MUS-03). The player puts a pickaxe into the menu of the Muse's table,
     * which has no shelves, while the Muse still waits, and the Muse's start makes the menu compute its offers again. They then equal,
     * cost for cost and clue for clue, the offers of a table with 8 real shelves to the same player, so vanilla's own rolls ran on the
     * new count.
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 1_000)
    fun cdMus03Lv50MuseGivesATableTheOffersOfEightShelves(helper: GameTestHelper) {
        val tableRel = BlockPos(11, 2, 6)
        val shelvedRel = BlockPos(17, 2, 17)
        val scene = scene(helper, BlockPos(11, 1, 11), tableRel)
        for (x in -2..2) helper.setBlock(shelvedRel.offset(x, 0, -2), Blocks.BOOKSHELF)
        for (z in -1..1) helper.setBlock(shelvedRel.offset(-2, 0, z), Blocks.BOOKSHELF)
        val enchanter = TestKit.owner(helper, "enchanter")
        var menu: EnchantmentMenu? = null
        var openedAt = -1L
        helper.onEachTick {
            val worker = TestKit.workerOf(helper, scene.muse) ?: return@onEachTick
            val open = menu
            if (open == null) {
                helper.assertTrue(worker.status != WorkerStatus.WORKING, "CD-MUS-05: the Muse Works with no menu open")
                if (!isStandbyOnTable(helper, scene.muse, tableRel)) return@onEachTick
                menu = openMenu(helper, enchanter, tableRel).also { it.getSlot(0).set(ItemStack(Items.DIAMOND_PICKAXE)) }
                openedAt = helper.tick
                return@onEachTick
            }
            if (worker.status != WorkerStatus.WORKING) {
                helper.assertTrue(helper.tick - openedAt <= 20, "CD-MUS-05: the Muse is ${worker.status} 20 ticks after the menu opened")
                return@onEachTick
            }
            val shelved = EnchantmentMenu(2, enchanter.inventory, ContainerLevelAccess.create(helper.level, helper.absolutePos(shelvedRel)))
            shelved.getSlot(0).set(ItemStack(Items.DIAMOND_PICKAXE))
            helper.assertTrue(shelved.costs[2] >= 16, "8 real shelves give a top cost of ${shelved.costs[2]}")
            helper.assertTrue(
                offers(open) == offers(shelved),
                "CD-MUS-03: the Muse's table offers ${offers(open)}, and the table with 8 shelves ${offers(shelved)}",
            )
            closeMenu(enchanter, open)
            helper.succeed()
        }
    }

    /**
     * The Muse waits at its table with no menu open for 600 ticks, and its Work Stamina never drops. Then a player keeps the table's
     * menu open for 600 ticks: the Muse Works within 20 ticks, and its Work Stamina drops on every tick after that (CD-MUS-05).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_800)
    fun cdMus05StaminaDrainsOnlyWhileTheMenuIsOpen(helper: GameTestHelper) {
        val tableRel = BlockPos(7, 2, 3)
        val scene = scene(helper, BlockPos(7, 1, 7), tableRel)
        val enchanter = TestKit.owner(helper, "enchanter")
        var step = "arrive"
        var since = -1L
        var stamina = 0.0
        var workingAt = -1L
        var menu: EnchantmentMenu? = null
        helper.onEachTick {
            val worker = TestKit.workerOf(helper, scene.muse) ?: return@onEachTick
            val fraction = worker.stamina.fraction
            when (step) {
                "arrive" -> if (isStandbyOnTable(helper, scene.muse, tableRel)) {
                    step = "closed"
                    since = helper.tick
                    stamina = fraction
                } else {
                    helper.assertTrue(worker.status != WorkerStatus.WORKING, "CD-MUS-05: the Muse Works with no menu open")
                }

                "closed" -> {
                    helper.assertTrue(worker.status == WorkerStatus.STANDBY, "CD-MUS-05: the Muse is ${worker.status} with no menu open")
                    helper.assertTrue(fraction >= stamina, "CD-MUS-05: Work Stamina fell from $stamina to $fraction with no menu open")
                    stamina = fraction
                    if (helper.tick - since >= 600) {
                        menu = openMenu(helper, enchanter, tableRel)
                        step = "open"
                        since = helper.tick
                    }
                }

                "open" -> {
                    if (workingAt < 0) {
                        if (worker.status == WorkerStatus.WORKING) {
                            workingAt = helper.tick
                        } else {
                            helper.assertTrue(
                                helper.tick - since <= 20,
                                "CD-MUS-05: the Muse is ${worker.status} 20 ticks after the menu opened",
                            )
                        }
                    } else {
                        helper.assertTrue(worker.status == WorkerStatus.WORKING, "the Muse stopped with the menu open: ${worker.status}")
                        helper.assertTrue(fraction < stamina, "CD-MUS-05: Work Stamina stayed at $fraction on a tick with the menu open")
                    }
                    stamina = fraction
                    if (helper.tick - since >= 600) {
                        menu?.let { closeMenu(enchanter, it) }
                        helper.succeed()
                    }
                }
            }
        }
    }

    /**
     * A leashed Clefairy Muse follows the worker rule, so it never takes a heal claim on its Tired ally in 1,200 ticks (CD-HEAL-14).
     * An unleashed Clefairy heals such an ally within 600 ticks (`HealerTests.cdHeal01HealerHealsASleepingAlly`).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_600)
    fun cdHeal14LeashedMuseNeverHealsATiredAlly(helper: GameTestHelper) {
        val scene = scene(helper, BlockPos(7, 1, 7), BlockPos(7, 2, 3))
        val machop = TestKit.tether(helper, scene.pasture, scene.owner, "machop level=10")
        val engine = TestKit.engine(helper)
        val museId = scene.muse.pokemon.uuid
        var since = -1L
        helper.onEachTick {
            val muse = engine.worker(museId) ?: return@onEachTick
            val ally = engine.worker(machop.pokemon.uuid) ?: return@onEachTick
            if (since < 0) {
                ally.stamina.fraction = 0.0
                ally.tired = true
                since = helper.tick
                return@onEachTick
            }
            helper.assertTrue(
                muse.leashed && engine.ruleOf(museId) == RecoveryRule.WORKER,
                "the bound Clefairy follows ${engine.ruleOf(museId)}",
            )
            val claim = engine.claims.claimOf(museId)
            helper.assertTrue(claim?.kind != ClaimKind.HEAL, "CD-HEAL-14: the leashed Muse holds $claim")
            helper.assertTrue(muse.status != WorkerStatus.HEALING, "CD-HEAL-14: the leashed Muse is HEALING")
            helper.assertTrue(ally.tired, "CD-HEAL-14: the ally is no longer Tired, at ${ally.stamina.percent}%")
            if (helper.tick - since >= 1_200) helper.succeed()
        }
    }
}
