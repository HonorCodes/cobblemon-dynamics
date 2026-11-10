package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.Feedback
import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.leash.BindingStore
import codes.honor.cobblemondynamics.registry.DynamicsAttachments
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.api.storage.pc.PCPosition
import com.cobblemon.mod.common.api.storage.pc.link.PCLinkManager
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.server.storage.pc.ReleasePCPokemonPacket
import com.cobblemon.mod.common.net.serverhandling.storage.party.ReleasePCPokemonHandler
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.trade.ActiveTrade
import com.cobblemon.mod.common.trade.PlayerTradeParticipant
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/** Each way that a binding ends without the owner's own leash action, against Cobblemon's real stores, trade, release and evolution. */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class LeashResetTests {
    private class Scene(val owner: ServerPlayer, val pasture: PokemonPastureBlockEntity)

    private class Bound(val owner: ServerPlayer, val pasture: PokemonPastureBlockEntity, val entity: PokemonEntity, val target: BlockPos)

    /** A Work Mode pasture at [rel] with its owner beside it. */
    private fun pasture(helper: GameTestHelper, rel: BlockPos = BlockPos(7, 1, 7)): Scene {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, rel, owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        return Scene(owner, pasture)
    }

    /** A Charmander of a new Work Mode pasture at (7,1,7), bound to an empty furnace at [furnaceRel]. */
    private fun boundCharmander(helper: GameTestHelper, furnaceRel: BlockPos = BlockPos(7, 1, 3)): Bound {
        val scene = pasture(helper)
        val target = TestKit.furnace(helper, furnaceRel, null).blockPos
        val fire = TestKit.tether(helper, scene.pasture, scene.owner, "charmander level=10")
        val binding = TestKit.bind(helper, scene.owner, fire, furnaceRel)
        helper.assertTrue(binding?.target == target, "the Charmander did not bind to its furnace: $binding")
        return Bound(scene.owner, scene.pasture, fire, target)
    }

    /** [pokemon] has no binding, [target] is not reserved, and its binder [owner] got the chat message with [reason] (CD-LSH-13). */
    private fun assertCleared(helper: GameTestHelper, pokemon: Pokemon, target: BlockPos, owner: ServerPlayer, reason: String) {
        helper.assertTrue(BindingStore.read(pokemon) == null, "the binding remains: ${BindingStore.read(pokemon)}")
        val reserved = TestKit.reservationAt(helper, target)
        helper.assertTrue(reserved == null, "the old target stays reserved: $reserved")
        val message = "${pokemon.getDisplayName().string} lost its work binding: $reason"
        val messages = TestKit.messages(owner)
        helper.assertTrue(message in messages, "CD-LSH-13: the binder got $messages, not '$message'")
    }

    /**
     * Four bound Charmanders lose their bindings in four ways, against Cobblemon's real PC and party: one is unpastured to the PC,
     * one is unpastured and moved to the party, one's furnace is destroyed, and one's furnace becomes stone.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 200)
    fun cdLsh08RecallAndTargetLoss(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        val furnaces = listOf(BlockPos(3, 1, 3), BlockPos(11, 1, 3), BlockPos(3, 1, 11), BlockPos(11, 1, 11))
        val pokemon = furnaces.map { rel ->
            TestKit.furnace(helper, rel, null)
            val fire = TestKit.tether(helper, pasture, owner, "charmander level=10")
            helper.assertTrue(TestKit.bind(helper, owner, fire, rel) != null, "the Charmander for $rel did not bind")
            fire.pokemon
        }
        val (toPc, toParty) = pokemon
        pasture.releasePokemon(toPc.uuid)
        pasture.releasePokemon(toParty.uuid)
        Cobblemon.storage.getPC(owner).remove(toParty)
        Cobblemon.storage.getParty(owner).add(toParty)
        helper.level.destroyBlock(helper.absolutePos(furnaces[2]), false)
        helper.setBlock(furnaces[3], Blocks.STONE)
        helper.runAfterDelay(20) {
            for ((i, it) in pokemon.withIndex()) {
                helper.assertTrue(BindingStore.read(it) == null, "Charmander $i keeps ${BindingStore.read(it)}")
            }
            val index = helper.level.getData(DynamicsAttachments.LEASH_INDEX)
            for (rel in furnaces) helper.assertTrue(index.get(helper.absolutePos(rel)) == null, "$rel is still reserved")
            val lost = Feedback.recorded[owner.uuid].orEmpty().map { it.string }.filter { "lost its work binding" in it }
            val left = "Charmander lost its work binding: it left its pasture"
            val gone = "Charmander lost its work binding: its target was broken or replaced"
            helper.assertTrue(lost.count { it == left } == 2 && lost.count { it == gone } == 2, "the binder's messages are $lost")
            helper.succeed()
        }
    }

    /**
     * A male Kirlia (Psychic and Fairy) is bound to an enchanting table as a Muse, and Cobblemon's own evolution into Gallade (Psychic and
     * Fighting) runs. No role of the Muse's handler fits Gallade, so 20 ticks later the binding and the reservation are gone (CD-LSH-14),
     * and the binder's chat names Gallade and the reason (CD-LSH-13).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 100)
    fun cdLsh13Lsh14EvolutionWithNoFittingRoleClearsTheBinding(helper: GameTestHelper) {
        val scene = pasture(helper)
        val tableRel = BlockPos(7, 2, 3)
        helper.setBlock(tableRel, Blocks.ENCHANTING_TABLE)
        val kirlia = TestKit.tether(helper, scene.pasture, scene.owner, "kirlia level=20 gender=male")
        val binding = TestKit.bind(helper, scene.owner, kirlia, tableRel)
        helper.assertTrue(binding?.roleId == id("muse"), "the Kirlia did not bind to the enchanting table as a Muse: $binding")
        val pokemon = kirlia.pokemon
        val evolution = pokemon.evolutions.firstOrNull { it.id == "kirlia_gallade" }
            ?: throw GameTestAssertException("Cobblemon's Kirlia has no evolution into Gallade")
        evolution.evolutionMethod(pokemon)
        val species = pokemon.species.resourceIdentifier
        helper.assertTrue(species == ResourceLocation.parse("cobblemon:gallade"), "the Kirlia evolved into $species")
        helper.runAfterDelay(20) {
            assertCleared(helper, pokemon, helper.absolutePos(tableRel), scene.owner, "its new form cannot do the job")
            helper.succeed()
        }
    }

    /**
     * A bound Charmander leaves pasture A and joins pasture B, as Cobblemon's panel moves it: unpastured on the tick of an index check,
     * and pastured again on the next tick, once its old entity has gone. 20 ticks later it has no binding, its furnace is free
     * (CD-LSH-24), and the binder knows that it moved (CD-LSH-13). Cobblemon discards the old entity on its first tick with a stale
     * tethering, and that discard clears the Pokémon's tethering, so a pasture that takes the Pokémon before then loses it again.
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 200)
    fun cdLsh24MovingToAnotherPastureClearsTheBinding(helper: GameTestHelper) {
        val first = pasture(helper, BlockPos(6, 1, 11))
        val second = TestKit.placePasture(helper, BlockPos(16, 1, 11), first.owner)
        TestKit.workMode(second, true)
        val furnaceRel = BlockPos(6, 1, 6)
        val furnace = TestKit.furnace(helper, furnaceRel, null).blockPos
        val fire = TestKit.tether(helper, first.pasture, first.owner, "charmander level=10")
        val pokemon = fire.pokemon
        helper.assertTrue(TestKit.bind(helper, first.owner, fire, furnaceRel)?.target == furnace, "the Charmander did not bind")
        var leftAt = -1L
        var movedAt = -1L
        helper.onEachTick {
            when {
                leftAt < 0 -> if (helper.level.gameTime % INDEX_CHECK_TICKS == 0L) {
                    first.pasture.releasePokemon(pokemon.uuid)
                    leftAt = helper.tick
                }

                movedAt < 0 -> {
                    helper.assertTrue(fire.isRemoved, "Cobblemon kept the Charmander's old entity a tick after it left pasture A")
                    helper.assertTrue(second.tether(first.owner, pokemon, Direction.SOUTH), "pasture B did not take the Charmander")
                    movedAt = helper.tick
                }

                helper.tick - movedAt >= 20 -> {
                    assertCleared(helper, pokemon, furnace, first.owner, "it moved to another pasture")
                    helper.succeed()
                }
            }
        }
    }

    /**
     * The owner releases a bound Charmander through Cobblemon's own release handler, as the PC's release button does. 20 ticks later its
     * furnace is free (CD-LSH-25), and the binder knows why (CD-LSH-13).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 100)
    fun cdLsh25ReleaseClearsTheBinding(helper: GameTestHelper) {
        val scene = boundCharmander(helper)
        val owner = scene.owner
        val pokemon = scene.entity.pokemon
        val pc = Cobblemon.storage.getPC(owner)
        val position = pokemon.storeCoordinates.get()?.position as? PCPosition
            ?: throw GameTestAssertException("the pastured Charmander is not in the PC")
        PCLinkManager.addLink(owner.uuid, pc)
        TestKit.afterTest(helper) { PCLinkManager.removeLink(owner.uuid) }
        ReleasePCPokemonHandler.handle(ReleasePCPokemonPacket(pokemon.uuid, position), helper.level.server, owner)
        helper.assertTrue(pc[pokemon.uuid] == null, "Cobblemon did not release the Charmander")
        helper.runAfterDelay(20) {
            assertCleared(helper, pokemon, scene.target, owner, "it was released")
            helper.succeed()
        }
    }

    /**
     * Owner A binds a Charmander, moves it to the party and trades it to owner B before the next index check, through Cobblemon's own
     * trade as the server runs it: both offers, both acceptances, then the trade. Owner B's gain clears the binding data and the
     * reservation at once (CD-LSH-26), and owner A learns why (CD-LSH-13).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 100)
    fun cdLsh26TradeClearsTheBindingData(helper: GameTestHelper) {
        val scene = boundCharmander(helper)
        val seller = scene.owner
        val pokemon = scene.entity.pokemon
        val buyer = TestKit.owner(helper, "buyer")
        val offer = TestKit.pokemon("rattata level=5")
        Cobblemon.storage.getParty(buyer).add(offer)
        scene.pasture.releasePokemon(pokemon.uuid)
        Cobblemon.storage.getPC(seller).remove(pokemon)
        Cobblemon.storage.getParty(seller).add(pokemon)
        helper.assertTrue(BindingStore.read(pokemon)?.binder == seller.uuid, "the binding did not stay with the Charmander in the party")
        val from = PlayerTradeParticipant(seller)
        val to = PlayerTradeParticipant(buyer)
        val trade = ActiveTrade(from, to)
        trade.updateOffer(from, pokemon)
        trade.updateOffer(to, offer)
        trade.updateAcceptance(from, true)
        trade.updateAcceptance(to, true)
        trade.performTrade(from)
        helper.assertTrue(pokemon in Cobblemon.storage.getParty(buyer), "the trade did not give owner B the Charmander")
        assertCleared(helper, pokemon, scene.target, seller, "it has a new owner")
        helper.succeed()
    }

    /**
     * The owner breaks a pasture whose three Charmanders are bound to three furnaces. 20 ticks later no binding remains and no furnace is
     * reserved (CD-LSH-27, CD-WMD-07), and the binder has one message for each Charmander (CD-LSH-13).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 100)
    fun cdLsh27Wmd07BreakingThePastureClearsEveryBinding(helper: GameTestHelper) {
        val scene = pasture(helper)
        val furnaces = listOf(BlockPos(3, 1, 3), BlockPos(7, 1, 3), BlockPos(11, 1, 3)).map { rel ->
            val target = TestKit.furnace(helper, rel, null).blockPos
            val fire = TestKit.tether(helper, scene.pasture, scene.owner, "charmander level=10")
            helper.assertTrue(TestKit.bind(helper, scene.owner, fire, rel)?.target == target, "the Charmander for $rel did not bind")
            fire.pokemon to target
        }
        scene.owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
        val pos = scene.pasture.blockPos
        helper.assertTrue(scene.owner.gameMode.destroyBlock(pos), "the owner could not break the pasture")
        helper.assertFalse(helper.level.getBlockState(pos).`is`(CobblemonBlocks.PASTURE), "the pasture still stands")
        helper.runAfterDelay(20) {
            for ((pokemon, target) in furnaces) {
                helper.assertTrue(BindingStore.read(pokemon) == null, "CD-LSH-27: a binding remains: ${BindingStore.read(pokemon)}")
                helper.assertTrue(TestKit.reservationAt(helper, target) == null, "CD-LSH-27: the furnace at $target is still reserved")
            }
            val messages = TestKit.messages(scene.owner)
            val lost = messages.count { it == "Charmander lost its work binding: its pasture block is gone" }
            helper.assertTrue(lost == furnaces.size, "CD-LSH-13: the binder got $lost of ${furnaces.size} messages: $messages")
            helper.succeed()
        }
    }

    private companion object {
        /** `LeashResets` checks the leash index on each tick whose game time is a multiple of this. */
        const val INDEX_CHECK_TICKS = 10L
    }
}
