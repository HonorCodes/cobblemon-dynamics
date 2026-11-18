package codes.honor.cobblemondynamics.compattest

import codes.honor.cobblemondynamics.Feedback
import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.MixinState
import codes.honor.cobblemondynamics.MuseStationMenu
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.handler.vanilla.MuseBonus
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.test.TestKit
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import fuzs.easymagic.world.inventory.ModEnchantmentMenu
import fuzs.enchantinginfuser.world.inventory.InfuserMenu
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.Container
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import java.lang.reflect.Field
import java.util.function.IntUnaryOperator

/**
 * The Muse at the real stations of Easy Magic v21.1.4 and Enchanting Infuser v21.1.4 (CD-FIX-05, CD-FIX-06), whose menus count their
 * power in private methods that the compat mixins hook. Fake players drop every menu that a block opens for them, so a test opens a
 * station as the station's own right-click does on the server: the block's menu provider makes the menu, the test sets the player's
 * `containerMenu` and posts NeoForge's `PlayerContainerEvent.Open`, as `ServerPlayer.openMenu` does, and the menu then takes the block's
 * container through `slotsChanged`. A test puts an item in as a click does: into the slot, then `broadcastChanges`, which runs the menu's
 * own slot listener. The power methods are private, so the tests call them by reflection.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class MuseCompatTests {
    private class Scene(val owner: ServerPlayer, val muse: PokemonEntity)

    /** The Muse's station, 5 blocks north of its pasture. */
    private val stationRel = BlockPos(11, 2, 6)
    private val pastureRel = BlockPos(11, 1, 11)

    /** A station of the same block with 15 real bookshelves, whose offers the Muse's station must give. */
    private val referenceRel = BlockPos(17, 2, 17)

    /**
     * CD-FIX-05: a level 100 Muse adds ceil(15 × 100 ÷ 100) = 15 bookshelves to Easy Magic's table, which has 5 real ones, within the
     * menu's maximum of 15. A player opens the table and puts a diamond pickaxe in while the Muse waits: Easy Magic's own power method,
     * `ModEnchantmentMenu.getEnchantingPower`, counts 5, and the top cost is at most 15, as 5 bookshelves give. Once the Muse Works, the
     * same method returns 15, not 20, and the menu's offers (its 3 costs and Easy Magic's clues) equal those of an Easy Magic table with
     * 15 real bookshelves for the same player and item, with a top cost of 30, as vanilla's cost rule gives at a power of 15. The table
     * is a Muse target through `#cobblemon_dynamics:muse_targets`, which gets it only from Easy Magic's own copy of the vanilla table's
     * tags when tags load, so the test checks the tag at run time.
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 1_000)
    fun cdFix05MuseAtEasyMagicTable(helper: GameTestHelper) {
        helper.assertTrue(
            MixinState.applied(MixinState.EASY_MAGIC_MENU),
            "CD-FIX-05: the hook of Easy Magic's ModEnchantmentMenu did not apply",
        )
        val table = block(EASY_MAGIC_TABLE)
        helper.assertTrue(
            table.defaultBlockState().`is`(MUSE_TARGETS),
            "CD-FIX-05: $EASY_MAGIC_TABLE is not in #cobblemon_dynamics:muse_targets after Easy Magic's copy of the vanilla table's tags",
        )
        helper.setBlock(stationRel, table)
        for (shelf in fiveShelves(stationRel)) helper.setBlock(shelf, Blocks.BOOKSHELF)
        helper.setBlock(referenceRel, table)
        for (shelf in fifteenShelves(referenceRel)) helper.setBlock(shelf, Blocks.BOOKSHELF)
        val scene = scene(helper, EASY_MAGIC_TABLE)
        val enchanter = TestKit.owner(helper, "enchanter")
        var menu: ModEnchantmentMenu? = null
        var openedAt = -1L
        helper.onEachTick {
            val worker = TestKit.workerOf(helper, scene.muse) ?: return@onEachTick
            val open = menu
            if (open == null) {
                helper.assertTrue(worker.status != WorkerStatus.WORKING, "CD-MUS-05: the Muse Works with no menu open")
                if (!isStandbyOnStation(helper, scene.muse)) return@onEachTick
                val opened = easyMagicMenu(helper, stationRel, enchanter, 1, open = true)
                putPickaxe(opened)
                val power = easyMagicPower(helper, opened, stationRel)
                helper.assertTrue(power == 5, "Easy Magic counts $power at the table with 5 bookshelves while the Muse waits")
                helper.assertTrue(opened.costs[2] <= 15, "5 bookshelves give a top cost of ${opened.costs[2]}")
                menu = opened
                openedAt = helper.tick
                return@onEachTick
            }
            if (worker.status != WorkerStatus.WORKING) {
                helper.assertTrue(helper.tick - openedAt <= 20, "CD-MUS-05: the Muse is ${worker.status} 20 ticks after the menu opened")
                return@onEachTick
            }
            val power = easyMagicPower(helper, open, stationRel)
            helper.assertTrue(power == 15, "CD-FIX-05: Easy Magic counts $power at the table with 5 bookshelves and the Muse's 15, not 15")
            val shelved = easyMagicMenu(helper, referenceRel, enchanter, 2, open = false)
            putPickaxe(shelved)
            val shelvedPower = easyMagicPower(helper, shelved, referenceRel)
            helper.assertTrue(shelvedPower == 15, "Easy Magic counts $shelvedPower at the table with 15 bookshelves")
            helper.assertTrue(
                offers(open) == offers(shelved),
                "CD-FIX-05: the Muse's table offers ${offers(open)}, and the table with 15 bookshelves ${offers(shelved)}",
            )
            helper.assertTrue(open.costs[2] == 30, "CD-FIX-05: the Muse's table offers a top cost of ${open.costs[2]}, not 30")
            close(enchanter, open)
            helper.succeed()
        }
    }

    /**
     * CD-FIX-06 at Enchanting Infuser's infuser: a level 100 Muse raises the available power of an infuser with no bookshelves to its
     * maximum of 15. See [museRaisesAnInfuser].
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 1_000)
    fun cdFix06MuseAtEnchantingInfuser(helper: GameTestHelper) = museRaisesAnInfuser(helper, INFUSER)

    /**
     * CD-FIX-06 at Enchanting Infuser's advanced infuser: a level 100 Muse raises the available power of an advanced infuser with no
     * bookshelves to its maximum of 15. See [museRaisesAnInfuser].
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 1_000)
    fun cdFix06MuseAtAdvancedEnchantingInfuser(helper: GameTestHelper) = museRaisesAnInfuser(helper, ADVANCED_INFUSER)

    /**
     * CD-FIX-06, the client part of `InfuserMenuMixin`, as far as a server shows it. No client runs, so the test proves two things only:
     * Mixin wove the `canEnchant` hook into the real `InfuserMenu` (`MixinState.handlerApplied("keepChoice")`, a handler that only this
     * mixin has), and on the server the hook changes nothing. A player with 100 levels chooses a level at an infuser with 15 bookshelves,
     * and the infuser's own `canEnchant` takes the choice. The test then puts the menu in the state that the infuser's message leaves on
     * a client after a Muse's start or stop: the chosen levels differ from the item's own, and `markedDirty` is false. On a client the
     * hook would mark the menu dirty again. On the server `canEnchant` stays false and `markedDirty` stays false. The play-test covers the
     * client: the enchant button after a Muse's start or stop.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 100)
    fun cdFix06InfuserClientHookIsWovenAndIdleOnTheServer(helper: GameTestHelper) {
        helper.assertTrue(MixinState.handlerApplied("keepChoice"), "CD-FIX-06: InfuserMenu.canEnchant calls no hook of InfuserMenuMixin")
        val infuserRel = BlockPos(7, 2, 7)
        helper.setBlock(infuserRel, block(INFUSER))
        for (shelf in fifteenShelves(infuserRel)) helper.setBlock(shelf, Blocks.BOOKSHELF)
        val enchanter = TestKit.owner(helper, "enchanter")
        enchanter.experienceLevel = 100
        val menu = infuserMenu(helper, infuserRel, enchanter, 1, open = false)
        putPickaxe(menu)
        helper.assertTrue(menu.getEnchantmentPower() == 15, "the infuser with 15 bookshelves has a power of ${menu.getEnchantmentPower()}")
        val choice = menu.getAllEnchantments().firstOrNull { menu.getAvailableEnchantmentLevel(it) > 0 }
            ?: throw GameTestAssertException("the infuser offers no level for a diamond pickaxe: ${levels(menu)}")
        val chosen = menu.clickEnchantmentLevelButton(choice, IntUnaryOperator { it + 1 })
        helper.assertTrue(
            chosen > 0 && menu.canEnchant(enchanter),
            "the infuser takes no choice of ${key(choice)}: level $chosen, cost ${menu.getEnchantingCost()}",
        )
        val markedDirty = markedDirtyField()
        markedDirty.setBoolean(menu, false)
        val enchants = menu.canEnchant(enchanter)
        helper.assertTrue(
            !enchants && !markedDirty.getBoolean(menu),
            "CD-FIX-06: on the server the client hook made canEnchant $enchants and markedDirty ${markedDirty.getBoolean(menu)}",
        )
        helper.succeed()
    }

    /**
     * CD-FIX-06: a level 100 Muse adds 15 bookshelves to the infuser [infuser], which has none, through the mixin at the return of
     * `InfuserMenu.getAvailablePower`, within the infuser's maximum, `getEnchantmentPowerLimit()`, of 15. A player opens the infuser
     * and puts a diamond pickaxe in while the Muse waits: its power is 0. Once the Muse Works, the private method returns 15, the menu's
     * power is 15, and the levels that the menu offers equal those of the same infuser with 15 real bookshelves. The menu names its block
     * through `MuseStationMenu`, which the mixin adds, so the Muse sees it open. With 5 real bookshelves placed next, the power stays
     * at 15 and never passes the maximum. The run has no Apothic Enchanting, with which the Muse takes no infuser and the power never
     * rises: `MuseBonusTest` covers that.
     */
    private fun museRaisesAnInfuser(helper: GameTestHelper, infuser: String) {
        helper.assertTrue(
            MixinState.applied(MixinState.INFUSER_MENU),
            "CD-FIX-06: the hook of Enchanting Infuser's InfuserMenu did not apply",
        )
        helper.assertTrue(!MuseBonus.withApothicEnchanting, "the compat run loads Apothic Enchanting, which this test needs absent")
        val block = block(infuser)
        helper.assertTrue(block.defaultBlockState().`is`(MUSE_TARGETS), "CD-FIX-06: $infuser is not in #cobblemon_dynamics:muse_targets")
        helper.setBlock(stationRel, block)
        helper.setBlock(referenceRel, block)
        for (shelf in fifteenShelves(referenceRel)) helper.setBlock(shelf, Blocks.BOOKSHELF)
        val scene = scene(helper, infuser)
        val enchanter = TestKit.owner(helper, "enchanter")
        var menu: InfuserMenu? = null
        var openedAt = -1L
        helper.onEachTick {
            val worker = TestKit.workerOf(helper, scene.muse) ?: return@onEachTick
            val open = menu
            if (open == null) {
                helper.assertTrue(worker.status != WorkerStatus.WORKING, "CD-MUS-05: the Muse Works with no menu open")
                if (!isStandbyOnStation(helper, scene.muse)) return@onEachTick
                val opened = infuserMenu(helper, stationRel, enchanter, 1, open = true)
                helper.assertTrue((opened as Any) is MuseStationMenu, "CD-FIX-06: the infuser's menu is no MuseStationMenu")
                putPickaxe(opened)
                val power = infuserPower(helper, opened, stationRel)
                helper.assertTrue(
                    power == 0 && opened.getEnchantmentPower() == 0,
                    "$infuser with no bookshelves has a power of $power, and its menu ${opened.getEnchantmentPower()}, while the Muse waits",
                )
                menu = opened
                openedAt = helper.tick
                return@onEachTick
            }
            if (worker.status != WorkerStatus.WORKING) {
                helper.assertTrue(helper.tick - openedAt <= 20, "CD-MUS-05: the Muse is ${worker.status} 20 ticks after the menu opened")
                return@onEachTick
            }
            val limit = open.getEnchantmentPowerLimit()
            helper.assertTrue(limit == 15, "$infuser has a limit of $limit, not its default of 15")
            val power = infuserPower(helper, open, stationRel)
            helper.assertTrue(
                power == 15 && open.getEnchantmentPower() == 15,
                "CD-FIX-06: the Muse's 15 bookshelves give $infuser a power of $power, and its menu ${open.getEnchantmentPower()}, not 15",
            )
            val shelved = infuserMenu(helper, referenceRel, enchanter, 2, open = false)
            putPickaxe(shelved)
            helper.assertTrue(
                shelved.getEnchantmentPower() == 15,
                "$infuser with 15 bookshelves has a power of ${shelved.getEnchantmentPower()}",
            )
            helper.assertTrue(
                levels(open) == levels(shelved),
                "CD-FIX-06: the Muse's $infuser offers ${levels(open)}, and the one with 15 bookshelves ${levels(shelved)}",
            )
            // The maximum: 5 real bookshelves and the Muse's 15 give 15, not 20.
            for (shelf in fiveShelves(stationRel)) helper.setBlock(shelf, Blocks.BOOKSHELF)
            open.slotsChanged(containerAt(helper, stationRel))
            val capped = infuserPower(helper, open, stationRel)
            helper.assertTrue(
                capped == 15 && open.getEnchantmentPower() == 15,
                "CD-FIX-06: 5 bookshelves and the Muse's 15 give $infuser a power of $capped, and its menu ${open.getEnchantmentPower()}",
            )
            close(enchanter, open)
            helper.succeed()
        }
    }

    /** A Work Mode pasture with a level 100 Clefairy that the Work Leash binds to the station [stationId] at [stationRel]. */
    private fun scene(helper: GameTestHelper, stationId: String): Scene {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, pastureRel, owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        val clefairy = TestKit.tether(helper, pasture, owner, "clefairy level=100")
        val binding = TestKit.bind(helper, owner, clefairy, stationRel)
        val reply = Feedback.recorded[owner.uuid].orEmpty().lastOrNull()?.string
        helper.assertTrue(
            binding?.target == helper.absolutePos(stationRel) && binding.roleId == id("muse"),
            "the bind to $stationId gave $binding and answered '$reply'",
        )
        return Scene(owner, clefairy)
    }

    private fun isStandbyOnStation(helper: GameTestHelper, muse: PokemonEntity): Boolean {
        val claim = TestKit.claimOf(helper, muse)
        return claim?.kind == ClaimKind.WORK &&
            claim.key == TestKit.keyOf(helper, helper.absolutePos(stationRel)) &&
            TestKit.workerOf(helper, muse)?.status == WorkerStatus.STANDBY
    }

    /**
     * The menu of the station at [rel] for [player], made as the station's right-click makes it on the server: by the block's menu
     * provider, then given the block's container. With [open], the player has the menu open, as `ServerPlayer.openMenu` leaves it.
     */
    private fun stationMenu(
        helper: GameTestHelper,
        rel: BlockPos,
        player: ServerPlayer,
        containerId: Int,
        open: Boolean,
    ): AbstractContainerMenu {
        val pos = helper.absolutePos(rel)
        val provider = helper.level.getBlockState(pos).getMenuProvider(helper.level, pos)
            ?: throw GameTestAssertException("the block at $rel gives no menu provider")
        val menu = provider.createMenu(containerId, player.inventory, player)
            ?: throw GameTestAssertException("the block at $rel makes no menu for ${player.gameProfile.name}")
        if (open) {
            player.containerMenu = menu
            NeoForge.EVENT_BUS.post(PlayerContainerEvent.Open(player, menu))
            TestKit.afterTest(helper) { close(player, menu) }
        }
        menu.slotsChanged(containerAt(helper, rel))
        return menu
    }

    private fun easyMagicMenu(
        helper: GameTestHelper,
        rel: BlockPos,
        player: ServerPlayer,
        containerId: Int,
        open: Boolean,
    ): ModEnchantmentMenu = stationMenu(helper, rel, player, containerId, open) as? ModEnchantmentMenu
        ?: throw GameTestAssertException("Easy Magic's table at $rel makes no ModEnchantmentMenu")

    private fun infuserMenu(helper: GameTestHelper, rel: BlockPos, player: ServerPlayer, containerId: Int, open: Boolean): InfuserMenu =
        stationMenu(helper, rel, player, containerId, open) as? InfuserMenu
            ?: throw GameTestAssertException("the infuser at $rel makes no InfuserMenu")

    private fun close(player: ServerPlayer, menu: AbstractContainerMenu) {
        if (player.containerMenu !== menu) return
        NeoForge.EVENT_BUS.post(PlayerContainerEvent.Close(player, menu))
        player.containerMenu = player.inventoryMenu
    }

    private fun containerAt(helper: GameTestHelper, rel: BlockPos): Container =
        helper.level.getBlockEntity(helper.absolutePos(rel)) as? Container
            ?: throw GameTestAssertException("the station at $rel has no container")

    /** A diamond pickaxe goes into the menu's item slot as a click puts it there, and the server's broadcast runs the slot listener. */
    private fun putPickaxe(menu: AbstractContainerMenu) {
        menu.getSlot(0).set(ItemStack(Items.DIAMOND_PICKAXE))
        menu.broadcastChanges()
    }

    /** Easy Magic's own count of the power of the table at [rel], with the hook of CD-FIX-05 at its return. */
    private fun easyMagicPower(helper: GameTestHelper, menu: ModEnchantmentMenu, rel: BlockPos): Int =
        privatePower(helper, menu, ModEnchantmentMenu::class.java, "getEnchantingPower", rel)

    /** Enchanting Infuser's own count of the power of the infuser at [rel], with the hook of CD-FIX-06 at its return. */
    private fun infuserPower(helper: GameTestHelper, menu: InfuserMenu, rel: BlockPos): Int =
        privatePower(helper, menu, InfuserMenu::class.java, "getAvailablePower", rel)

    private fun privatePower(helper: GameTestHelper, menu: Any, owner: Class<*>, name: String, rel: BlockPos): Int {
        val method = owner.getDeclaredMethod(name, Level::class.java, BlockPos::class.java)
        method.isAccessible = true
        return method.invoke(menu, helper.level, helper.absolutePos(rel)) as Int
    }

    private fun markedDirtyField(): Field = InfuserMenu::class.java.getDeclaredField("markedDirty").apply { isAccessible = true }

    /** The table's offers: its 3 costs, and Easy Magic's clue for each as enchantment and level. */
    private fun offers(menu: ModEnchantmentMenu): List<String> = menu.costs.map { "cost $it" } +
        menu.clues.map { clue -> clue.joinToString(prefix = "clue ") { "${key(it.enchantment)} ${it.level}" } }

    /** The highest level of each enchantment that the infuser's power offers for its item. */
    private fun levels(menu: InfuserMenu): Map<String, Int> =
        menu.getAllEnchantments().associate { key(it) to menu.getAvailableEnchantmentLevel(it) }

    private fun key(enchantment: Holder<Enchantment>): String =
        enchantment.unwrapKey().map { it.location().toString() }.orElse(enchantment.toString())

    /** 5 bookshelves in a row 2 blocks north of [table], with air between each and the table. */
    private fun fiveShelves(table: BlockPos): List<BlockPos> = (-2..2).map { table.offset(it, 0, -2) }

    /** 15 bookshelves of vanilla's ring around [table]: 2 rows of 5 to the north, 3 to the west and 2 to the east, all with air between. */
    private fun fifteenShelves(table: BlockPos): List<BlockPos> = (0..1).flatMap { y -> (-2..2).map { table.offset(it, y, -2) } } +
        (-1..1).map { table.offset(-2, 0, it) } +
        (-1..0).map { table.offset(2, 0, it) }

    private fun block(id: String): Block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id)).orElseThrow {
        GameTestAssertException("the compat run has no block $id")
    }

    private companion object {
        const val EASY_MAGIC_TABLE = "easymagic:enchanting_table"
        const val INFUSER = "enchantinginfuser:enchanting_infuser"
        const val ADVANCED_INFUSER = "enchantinginfuser:advanced_enchanting_infuser"
        val MUSE_TARGETS: TagKey<Block> = TagKey.create(Registries.BLOCK, id("muse_targets"))
    }
}
