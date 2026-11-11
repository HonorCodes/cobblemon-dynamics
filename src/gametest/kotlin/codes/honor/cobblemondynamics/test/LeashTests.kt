package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.leash.BindingStore
import codes.honor.cobblemondynamics.leash.LeashComponents
import codes.honor.cobblemondynamics.net.LeashOverviewPayload
import codes.honor.cobblemondynamics.registry.DynamicsItems
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/**
 * The Work Leash through the real interaction pipeline: `Player.interactOn` for Pokémon and `ServerPlayerGameMode.useItemOn` for
 * blocks, so NeoForge's events, Cobblemon's handlers and `Item#onItemUseFirst` run as for a real click.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class LeashTests {
    private class Scene(val owner: ServerPlayer, val pasture: PokemonPastureBlockEntity, val pokemon: PokemonEntity)

    /** A Work Mode pasture at (7,1,7) with one Pokémon of [spec], and its owner beside the pasture. */
    private fun scene(helper: GameTestHelper, spec: String, owner: ServerPlayer = TestKit.owner(helper)): Scene {
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        return Scene(owner, pasture, TestKit.tether(helper, pasture, owner, spec))
    }

    private fun isWorkingAt(helper: GameTestHelper, entity: PokemonEntity, furnace: BlockPos): Boolean {
        val claim = TestKit.claimOf(helper, entity)
        return claim?.kind == ClaimKind.WORK &&
            claim.key == TestKit.keyOf(helper, furnace) &&
            TestKit.workerOf(helper, entity)?.status == WorkerStatus.WORKING
    }

    @GameTest(template = "floor_15x6x15")
    fun cdLsh01SelectsOwnedPasturedPokemon(helper: GameTestHelper) {
        val scene = scene(helper, "vaporeon level=30")
        val stack = TestKit.leash(scene.owner)
        val result = TestKit.clickEntity(scene.owner, scene.pokemon)
        helper.assertTrue(result.consumesAction(), "the click returned $result")
        val selection = stack.get(LeashComponents.SELECTION.get())
        helper.assertTrue(selection?.pokemonId == scene.pokemon.pokemon.uuid, "the leash holds $selection, not the Vaporeon")
        helper.succeed()
    }

    /**
     * `Player.interactOn` posts the event, returns at once when it is cancelled, and only then calls the entity, where Cobblemon's
     * `mobInteract` opens its wheel or mounts the Pokémon. The last listener marks every click that is not cancelled; the
     * empty-hand click shows that it sees a click that reaches Cobblemon.
     */
    @GameTest(template = "floor_15x6x15")
    fun cdLsh02ClickIsCancelledBeforeCobblemon(helper: GameTestHelper) {
        val scene = scene(helper, "vaporeon level=30")
        val data = scene.pokemon.persistentData
        TestKit.clickEntity(scene.owner, scene.pokemon)
        helper.assertTrue(data.getBoolean(REACHED_ENTITY_INTERACTION), "the empty-hand click was not marked")
        data.remove(REACHED_ENTITY_INTERACTION)
        TestKit.leash(scene.owner)
        for (sneak in listOf(false, true)) {
            val result = TestKit.clickEntity(scene.owner, scene.pokemon, sneak)
            val click = if (sneak) "sneak click" else "plain click"
            helper.assertTrue(result == InteractionResult.SUCCESS, "the $click returned $result")
            helper.assertFalse(data.contains(REACHED_ENTITY_INTERACTION), "the $click reached the Pokémon's own interaction")
        }
        val shoulders = scene.owner.shoulderEntityLeft.isEmpty && scene.owner.shoulderEntityRight.isEmpty
        helper.assertTrue(shoulders && scene.pokemon.vehicle == null, "the Vaporeon sits on the player")
        helper.succeed()
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdLsh03BindsFurnaceAndWorksIt(helper: GameTestHelper) {
        val scene = scene(helper, "charmander level=10")
        val furnace = TestKit.furnace(helper, BlockPos(7, 1, 3), ItemStack(Items.RAW_IRON, 8)).blockPos
        TestKit.leash(scene.owner)
        TestKit.clickEntity(scene.owner, scene.pokemon)
        TestKit.clickBlock(helper, scene.owner, BlockPos(7, 1, 3))
        helper.runAfterDelay(1) {
            val binding = BindingStore.read(scene.pokemon.pokemon)
            helper.assertTrue(binding?.target == furnace, "no binding to the furnace 1 tick after the click: $binding")
        }
        helper.succeedWhen {
            helper.assertTrue(TestKit.workerOf(helper, scene.pokemon)?.leashed == true, "the bound Charmander is not leashed")
            helper.assertTrue(isWorkingAt(helper, scene.pokemon, furnace), "the bound Charmander does not work its furnace")
        }
    }

    /** The empty-hand click shows that the counting owner sees the furnace screen open. */
    @GameTest(template = "floor_15x6x15")
    fun cdLsh04FurnaceScreenStaysShut(helper: GameTestHelper) {
        val owner = TestKit.menuCountingOwner(helper)
        val scene = scene(helper, "charmander level=10", owner)
        TestKit.furnace(helper, BlockPos(7, 1, 3), null)
        TestKit.clickBlock(helper, owner, BlockPos(7, 1, 3))
        helper.assertTrue(owner.menusOpened == 1, "the empty-hand click opened ${owner.menusOpened} menus")
        TestKit.leash(owner)
        TestKit.clickEntity(owner, scene.pokemon)
        TestKit.clickBlock(helper, owner, BlockPos(7, 1, 3))
        helper.assertTrue(BindingStore.read(scene.pokemon.pokemon) != null, "the leash click did not bind")
        helper.assertTrue(owner.menusOpened == 1, "the leash click opened the furnace screen")
        helper.succeed()
    }

    /**
     * Before the restart, the Pokémon goes through its save codec. After it, the binding stays through Work Mode off and on, and the
     * worker works its furnace again. The restart drops the whole engine, so this test runs alone in its own batch.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_200, batch = "restart_lsh07")
    fun cdLsh07BindingSurvivesRestartAndToggle(helper: GameTestHelper) {
        val scene = scene(helper, "charmander level=10")
        val furnace = TestKit.furnace(helper, BlockPos(7, 1, 3), ItemStack(Items.RAW_IRON, 8)).blockPos
        val bound = TestKit.bind(helper, scene.owner, scene.pokemon, BlockPos(7, 1, 3))
        helper.assertTrue(bound?.target == furnace, "no binding to the furnace: $bound")
        var step = "bound"
        var since = 0L
        helper.onEachTick {
            val binding = BindingStore.read(scene.pokemon.pokemon)
            helper.assertTrue(binding == bound, "the binding is $binding after the step '$step'")
            val elapsed = helper.tick - since
            when (step) {
                "bound" -> if (isWorkingAt(helper, scene.pokemon, furnace)) {
                    val access = helper.level.registryAccess()
                    val saved = Pokemon.loadFromNBT(access, scene.pokemon.pokemon.saveToNBT(access))
                    helper.assertTrue(BindingStore.read(saved) == bound, "the saved Pokémon holds ${BindingStore.read(saved)}")
                    TestKit.simulateRestart(helper)
                    step = "restarted"
                    since = helper.tick
                }

                "restarted" -> if (elapsed >= 5) {
                    TestKit.workMode(scene.pasture, false)
                    step = "off"
                    since = helper.tick
                }

                "off" -> if (elapsed >= 5) {
                    TestKit.workMode(scene.pasture, true)
                    step = "on"
                }

                "on" -> if (isWorkingAt(helper, scene.pokemon, furnace)) helper.succeed()
            }
        }
    }

    /**
     * CD-UI-09: the client outlines the targets in the server's overview reply, and each reply holds only the asking player's
     * bindings. Two owners in one level bind one furnace each, and each reply names that owner's furnace alone.
     */
    @GameTest(template = "floor_41x8x41")
    fun cdUi09OverviewHoldsOnlyOwnTargets(helper: GameTestHelper) {
        val scenes = listOf(BlockPos(10, 1, 10), BlockPos(30, 1, 30)).map { pastureRel ->
            val owner = TestKit.owner(helper)
            val pasture = TestKit.placePasture(helper, pastureRel, owner)
            TestKit.workMode(pasture, true)
            TestKit.standBeside(owner, pasture)
            val pokemon = TestKit.tether(helper, pasture, owner, "charmander level=10")
            val furnaceRel = pastureRel.north(4)
            val furnace = TestKit.furnace(helper, furnaceRel, ItemStack(Items.RAW_IRON, 8)).blockPos
            val binding = TestKit.bind(helper, owner, pokemon, furnaceRel)
            helper.assertTrue(binding?.target == furnace, "the Charmander of ${owner.gameProfile.name} is bound to $binding")
            owner to furnace
        }
        for ((owner, furnace) in scenes) {
            val targets = LeashOverviewPayload.of(owner).entries.map { it.second }
            helper.assertTrue(targets == listOf(furnace), "the overview of ${owner.gameProfile.name} holds $targets, not only $furnace")
        }
        helper.succeed()
    }

    /** Clicks [face] of the block at [rel] with [player]'s leash, and returns the reply that the action bar shows. */
    private fun replyTo(helper: GameTestHelper, player: ServerPlayer, rel: BlockPos, face: Direction = Direction.UP): String? {
        TestKit.clickBlock(helper, player, rel, face)
        return TestKit.lastMessage(player)
    }

    /**
     * Each broken rule refuses with its own message and binds nothing (CD-LSH-05): a furnace at horizontal offset 9, a stone block, a
     * furnace bound to a Vulpix, and a selection whose Charmander has left the pasture. The furnace at offset 8 binds, and the action
     * bar names the Charmander and the furnace (CD-LSH-06).
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 100)
    fun cdLsh05Lsh06RefusalsAndTheBoundMessage(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(11, 1, 11), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        val beyond = BlockPos(2, 1, 11)
        val edge = BlockPos(19, 1, 11)
        val taken = BlockPos(11, 1, 6)
        val spare = BlockPos(11, 1, 16)
        for (rel in listOf(beyond, edge, taken, spare)) TestKit.furnace(helper, rel, ItemStack(Items.RAW_IRON, 8))
        val vulpix = TestKit.tether(helper, pasture, owner, "vulpix level=10")
        helper.assertTrue(TestKit.bind(helper, owner, vulpix, taken) != null, "the Vulpix did not bind")
        val fire = TestKit.tether(helper, pasture, owner, "charmander level=10")
        TestKit.leash(owner)
        TestKit.clickEntity(owner, fire)
        val refusals = listOf(
            beyond to "Outside its pasture's work area",
            BlockPos(14, 1, 14) to "No role fits",
            taken to "Already assigned to Vulpix",
        )
        for ((rel, expected) in refusals) {
            val reply = replyTo(helper, owner, rel)
            helper.assertTrue(reply == expected, "CD-LSH-05: the click on the ${helper.getBlockState(rel).block} at $rel answered '$reply'")
            helper.assertTrue(BindingStore.read(fire.pokemon) == null, "CD-LSH-05: the refused click at $rel bound the Charmander")
        }
        val vulpixHolds = TestKit.reservationAt(helper, helper.absolutePos(taken))?.pokemonId == vulpix.pokemon.uuid
        helper.assertTrue(vulpixHolds, "the refused click took the Vulpix's furnace")
        val bound = replyTo(helper, owner, edge)
        helper.assertTrue(bound == "Bound Charmander to Furnace", "CD-LSH-06: the bind at offset 8 answered '$bound'")
        val binding = BindingStore.read(fire.pokemon)
        helper.assertTrue(binding?.target == helper.absolutePos(edge), "CD-LSH-05: the furnace at offset 8 gave $binding")
        val gone = TestKit.tether(helper, pasture, owner, "charmander level=10")
        TestKit.leash(owner)
        TestKit.clickEntity(owner, gone)
        pasture.releasePokemon(gone.pokemon.uuid)
        val left = replyTo(helper, owner, spare)
        helper.assertTrue(left == "Not in a Work Mode pasture", "CD-LSH-05: the click for a Charmander out of its pasture answered '$left'")
        helper.assertTrue(BindingStore.read(gone.pokemon) == null, "CD-LSH-05: a Charmander that left its pasture was bound")
        helper.succeed()
    }

    /**
     * The owner sneak-clicks a Vaporeon that the Work Leash bound to a cauldron as a Pump. 1 tick later the binding and the reservation
     * are gone (CD-LSH-10), and the owner's own action sent no chat message (CD-LSH-13).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 100)
    fun cdLsh10SneakClickClearsTheBinding(helper: GameTestHelper) {
        val cauldronRel = BlockPos(7, 1, 3)
        helper.setBlock(cauldronRel, Blocks.CAULDRON)
        val scene = scene(helper, "vaporeon level=30")
        val bound = TestKit.bind(helper, scene.owner, scene.pokemon, cauldronRel)
        helper.assertTrue(bound?.roleId == id("pump"), "the Vaporeon did not bind to the cauldron as a Pump: $bound")
        TestKit.leash(scene.owner)
        TestKit.clickEntity(scene.owner, scene.pokemon, sneak = true)
        helper.runAfterDelay(1) {
            val binding = BindingStore.read(scene.pokemon.pokemon)
            helper.assertTrue(binding == null, "CD-LSH-10: 1 tick after the sneak-click the binding is $binding")
            val reserved = TestKit.reservationAt(helper, helper.absolutePos(cauldronRel))
            helper.assertTrue(reserved == null, "CD-LSH-10: the cauldron is still reserved: $reserved")
            val messages = TestKit.messages(scene.owner)
            helper.assertTrue("Cleared the binding of Vaporeon" in messages, "the sneak-click sent $messages")
            helper.assertTrue(messages.none { "lost its work binding" in it }, "CD-LSH-13: the owner's own sneak-click sent $messages")
            helper.succeed()
        }
    }

    /**
     * A Charmander bound to furnace A works it, and the owner binds it to furnace B. From then on furnace A is not reserved, the binding
     * names furnace B (CD-LSH-11), and the Charmander goes on to work furnace B. The owner's own rebind sends no chat message.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdLsh11RebindReplacesTheBinding(helper: GameTestHelper) {
        val scene = scene(helper, "charmander level=10")
        val relA = BlockPos(3, 1, 3)
        val relB = BlockPos(11, 1, 3)
        val furnaceA = TestKit.furnace(helper, relA, ItemStack(Items.RAW_IRON, 8)).blockPos
        val furnaceB = TestKit.furnace(helper, relB, ItemStack(Items.RAW_IRON, 8)).blockPos
        helper.assertTrue(TestKit.bind(helper, scene.owner, scene.pokemon, relA)?.target == furnaceA, "no binding to furnace A")
        val charmander = scene.pokemon.pokemon.uuid
        var rebound = false
        helper.onEachTick {
            if (!rebound) {
                if (!isWorkingAt(helper, scene.pokemon, furnaceA)) return@onEachTick
                val binding = TestKit.bind(helper, scene.owner, scene.pokemon, relB)
                helper.assertTrue(binding?.target == furnaceB, "CD-LSH-11: the second bind gave $binding")
                rebound = true
            }
            helper.assertTrue(TestKit.reservationAt(helper, furnaceA) == null, "CD-LSH-11: furnace A stays reserved after the rebind")
            val reservedForIt = TestKit.reservationAt(helper, furnaceB)?.pokemonId == charmander
            helper.assertTrue(reservedForIt, "furnace B is not reserved for the Charmander")
            val binding = BindingStore.read(scene.pokemon.pokemon)
            helper.assertTrue(binding?.target == furnaceB, "CD-LSH-11: the Charmander's binding is $binding")
            val messages = TestKit.messages(scene.owner)
            helper.assertTrue(messages.none { "lost its work binding" in it }, "the owner's own rebind sent $messages")
            if (isWorkingAt(helper, scene.pokemon, furnaceB)) helper.succeed()
        }
    }

    /** A Lead and a Poké Ball in any two slots of either crafting grid make one Work Leash, through the loaded recipe (CD-LSH-15). */
    @GameTest(template = "floor_9x5x9")
    fun cdLsh15RecipeCraftsOneLeash(helper: GameTestHelper) {
        val level = helper.level
        val ball = BuiltInRegistries.ITEM.get(ResourceLocation.parse("cobblemon:poke_ball"))
        helper.assertTrue(ball != Items.AIR, "Cobblemon registers no cobblemon:poke_ball")
        for (width in listOf(2, 3)) {
            val slots = width * width
            for (lead in 0 until slots) {
                for (pokeBall in 0 until slots) {
                    if (lead == pokeBall) continue
                    val items = MutableList(slots) { ItemStack.EMPTY }
                    items[lead] = ItemStack(Items.LEAD)
                    items[pokeBall] = ItemStack(ball)
                    val input = CraftingInput.of(width, width, items)
                    val result = level.recipeManager.getRecipeFor(RecipeType.CRAFTING, input, level)
                        .map { it.value().assemble(input, level.registryAccess()) }
                        .orElse(ItemStack.EMPTY)
                    helper.assertTrue(
                        result.`is`(DynamicsItems.WORK_LEASH.get()) && result.count == 1,
                        "CD-LSH-15: a Lead in slot $lead and a Poké Ball in slot $pokeBall of the $width × $width grid make $result",
                    )
                }
            }
        }
        helper.succeed()
    }

    /**
     * The real Telekinesis role binds a chest to a barrel (CD-TLK-01). A click on a chest's top face with a selected Alakazam stores the
     * chest and the face as the pending source and binds nothing (CD-LSH-17), and the action bar asks for a destination (CD-LSH-20).
     * The click on a barrel's north face then binds the chest as the source and the barrel as the destination, with both faces
     * (CD-LSH-18).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 100)
    fun cdLsh17Lsh18Lsh20SourceThenDestination(helper: GameTestHelper) {
        val chestRel = BlockPos(5, 1, 3)
        val barrelRel = BlockPos(9, 1, 3)
        helper.setBlock(chestRel, Blocks.CHEST)
        helper.setBlock(barrelRel, Blocks.BARREL)
        val chest = helper.absolutePos(chestRel)
        val barrel = helper.absolutePos(barrelRel)
        val scene = scene(helper, "alakazam level=40")
        val stack = TestKit.leash(scene.owner)
        TestKit.clickEntity(scene.owner, scene.pokemon)
        val asked = replyTo(helper, scene.owner, chestRel, Direction.UP)
        val pending = stack.get(LeashComponents.PENDING_SOURCE.get())
        helper.assertTrue(pending?.pos == chest && pending.face == Direction.UP, "CD-LSH-17: the leash holds $pending")
        helper.assertTrue(BindingStore.read(scene.pokemon.pokemon) == null, "CD-LSH-17: the source click bound the Alakazam")
        helper.assertTrue(asked == "Select a destination for Alakazam", "CD-LSH-20: the source click answered '$asked'")
        TestKit.clickBlock(helper, scene.owner, barrelRel, Direction.NORTH)
        helper.runAfterDelay(1) {
            val binding = BindingStore.read(scene.pokemon.pokemon)
            val source = binding?.roleId == id("telekinesis") && binding.target == chest && binding.face == Direction.UP
            val destination = binding?.destination == barrel && binding.destinationFace == Direction.NORTH
            helper.assertTrue(source && destination, "CD-LSH-18: the destination click gave $binding")
            helper.assertFalse(stack.has(LeashComponents.PENDING_SOURCE.get()), "the leash keeps its pending source after the bind")
            helper.succeed()
        }
    }

    /**
     * A chest click with an Alakazam selected stores a pending source of the real Telekinesis role. A right-click on air clears a
     * pending source at once. A new pending source is still on the stack 599 ticks after its click and is gone at 600, on the inventory
     * tick that a player's inventory runs every tick (CD-LSH-19). Fake players never tick their inventory, so the test runs that tick
     * itself.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdLsh19AirClickAndTimeoutClearThePendingSource(helper: GameTestHelper) {
        val chestRel = BlockPos(5, 1, 3)
        helper.setBlock(chestRel, Blocks.CHEST)
        val scene = scene(helper, "alakazam level=40")
        val owner = scene.owner
        val stack = TestKit.leash(owner)
        val pending = LeashComponents.PENDING_SOURCE.get()
        TestKit.clickEntity(owner, scene.pokemon)
        TestKit.clickBlock(helper, owner, chestRel)
        helper.assertTrue(stack.has(pending), "the chest click stored no pending source")
        owner.gameMode.useItem(owner, helper.level, stack, InteractionHand.MAIN_HAND)
        helper.assertFalse(stack.has(pending), "CD-LSH-19: a right-click on air left the pending source")
        TestKit.clickBlock(helper, owner, chestRel)
        val clickedAt = stack.get(pending)?.clickedAt ?: throw GameTestAssertException("the second chest click stored no pending source")
        helper.onEachTick {
            val age = helper.level.gameTime - clickedAt
            if (age < 599) return@onEachTick
            stack.inventoryTick(helper.level, owner, owner.inventory.selected, true)
            if (age == 599L) {
                helper.assertTrue(stack.has(pending), "CD-LSH-19: the pending source is gone 599 ticks after its click")
            } else {
                helper.assertFalse(stack.has(pending), "CD-LSH-19: the pending source remains $age ticks after its click")
                helper.succeed()
            }
        }
    }

    /** Player B's Work Leash selects neither player A's pastured Charmander nor a wild one, and player A's own click does (CD-SEC-07). */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 100)
    fun cdSec07OnlyTheOwnerSelects(helper: GameTestHelper) {
        val scene = scene(helper, "charmander level=10")
        val wild = TestKit.wild(helper, BlockPos(3, 2, 11), "charmander level=10")
        val stranger = TestKit.owner(helper, "stranger")
        TestKit.standBeside(stranger, scene.pasture)
        val stack = TestKit.leash(stranger)
        for ((what, target) in listOf("player A's pastured" to scene.pokemon, "wild" to wild)) {
            TestKit.clickEntity(stranger, target)
            val selection = stack.get(LeashComponents.SELECTION.get())
            helper.assertTrue(selection == null, "CD-SEC-07: player B's leash selected the $what Charmander: $selection")
        }
        val own = TestKit.leash(scene.owner)
        TestKit.clickEntity(scene.owner, scene.pokemon)
        val selected = own.get(LeashComponents.SELECTION.get())?.pokemonId == scene.pokemon.pokemon.uuid
        helper.assertTrue(selected, "player A's own click selected nothing, so the scene proves nothing")
        helper.succeed()
    }

    /**
     * A listener cancels right-clicks on one chest, as a protection mod does, and denies item use on another. Clicks on either with a
     * selected Alakazam, which the real Telekinesis role fits, store no pending source and bind nothing (CD-SEC-11). A free chest then
     * becomes the pending source, which shows that the selection works, and a click on the cancelled chest as the destination binds
     * nothing either.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 100)
    fun cdSec11CancelledClickNeverBinds(helper: GameTestHelper) {
        val cancelledRel = BlockPos(4, 1, 3)
        val deniedRel = BlockPos(7, 1, 3)
        val freeRel = BlockPos(10, 1, 3)
        for (rel in listOf(cancelledRel, deniedRel, freeRel)) helper.setBlock(rel, Blocks.CHEST)
        val cancelled = helper.absolutePos(cancelledRel)
        val denied = helper.absolutePos(deniedRel)
        ProtectedBlocks.cancelled += cancelled
        ProtectedBlocks.itemUseDenied += denied
        TestKit.afterTest(helper) {
            ProtectedBlocks.cancelled -= cancelled
            ProtectedBlocks.itemUseDenied -= denied
        }
        val scene = scene(helper, "alakazam level=40")
        val stack = TestKit.leash(scene.owner)
        val pending = LeashComponents.PENDING_SOURCE.get()
        TestKit.clickEntity(scene.owner, scene.pokemon)
        for ((what, rel) in listOf("cancelled" to cancelledRel, "item-denied" to deniedRel)) {
            TestKit.clickBlock(helper, scene.owner, rel)
            helper.assertFalse(stack.has(pending), "CD-SEC-11: the $what chest became the pending source: ${stack.get(pending)}")
            helper.assertTrue(BindingStore.read(scene.pokemon.pokemon) == null, "CD-SEC-11: the $what chest was bound")
        }
        TestKit.clickBlock(helper, scene.owner, freeRel)
        helper.assertTrue(stack.get(pending)?.pos == helper.absolutePos(freeRel), "the free chest did not become the pending source")
        TestKit.clickBlock(helper, scene.owner, cancelledRel)
        val binding = BindingStore.read(scene.pokemon.pokemon)
        helper.assertTrue(binding == null, "CD-SEC-11: a click on the cancelled chest as the destination bound $binding")
        helper.succeed()
    }
}
