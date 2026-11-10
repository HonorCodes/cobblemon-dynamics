package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.leash.BindingStore
import codes.honor.cobblemondynamics.leash.LeashComponents
import codes.honor.cobblemondynamics.net.LeashOverviewPayload
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
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
}
