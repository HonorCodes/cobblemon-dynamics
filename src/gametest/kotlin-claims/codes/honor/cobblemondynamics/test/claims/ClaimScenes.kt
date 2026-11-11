package codes.honor.cobblemondynamics.test.claims

import codes.honor.cobblemondynamics.test.TestKit
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * The scene of the three claim runs, which names no class of a claim mod: furnace A with 8 raw iron at [A] of `floor_23x10x23`, and
 * plain owners at [OWNER] who look at A (CD-QWK-27 to CD-QWK-32, CD-LND-09, CD-LND-10). Every claim test has its own batch, because a
 * land claim covers a whole chunk, and a colony covers the chunks around its center.
 */
object ClaimScenes {
    val A = BlockPos(4, 2, 11)
    val OWNER = BlockPos(14, 2, 11)

    /** A furnace 16 blocks east of A, so always in another chunk than A. */
    val UNCLAIMED = BlockPos(20, 2, 11)

    const val REFUSAL = "You may not use Furnace"

    fun furnaceA(helper: GameTestHelper): BlockPos = TestKit.furnace(helper, A, ItemStack(Items.RAW_IRON, 8)).blockPos

    /** A plain owner at [rel] who looks at [at]. */
    fun owner(helper: GameTestHelper, name: String, rel: BlockPos = OWNER, at: BlockPos = A, permissionLevel: Int = 0): TestKit.PlainOwner {
        val owner = TestKit.plainOwner(helper, rel, name, permissionLevel)
        TestKit.lookAt(helper, owner.player, at)
        return owner
    }

    /** Presses R for party [slot] while [owner] looks at [rel], and checks that the send-out of [pokemon] aimed at that block. */
    fun sendOutAt(helper: GameTestHelper, owner: ServerPlayer, pokemon: Pokemon, rel: BlockPos = A, slot: Int = 0) {
        TestKit.lookAt(helper, owner, rel)
        TestKit.pressSendOut(owner, slot)
        val aimed = TestKit.engine(helper).aimOf(pokemon.uuid)?.pos
        helper.assertTrue(aimed == helper.absolutePos(rel), "the send-out of the ${pokemon.species.name} aimed at $aimed, not at $rel")
    }

    fun hasOrder(helper: GameTestHelper, pokemon: Pokemon): Boolean = TestKit.engine(helper).isQuickWorker(pokemon.uuid)

    /** On every tick, [pokemon] has no order and no claim of any kind, so its send-out stays a normal one. */
    fun assertNoOrder(helper: GameTestHelper, row: String, pokemon: Pokemon) {
        helper.onEachTick {
            helper.assertFalse(hasOrder(helper, pokemon), "$row: the ${pokemon.species.name} got an order")
            val claim = TestKit.engine(helper).claims.claimOf(pokemon.uuid)
            helper.assertTrue(claim == null, "$row: the ${pokemon.species.name} holds $claim")
        }
    }

    /** The chat and action-bar packets that [owner] got after the first [from] packets: system chat, either kind, and action bars. */
    fun messagePackets(owner: TestKit.PlainOwner, from: Int = 0): List<Any> = owner.packets.drop(from).filter {
        it is ClientboundSystemChatPacket || it is ClientboundSetActionBarTextPacket
    }

    /** A Work Mode pasture of [placer] at [rel] with one Charmander, whose work area holds furnace A (CD-LND-10). */
    fun pastureOf(
        helper: GameTestHelper,
        placer: ServerPlayer,
        rel: BlockPos = BlockPos(8, 1, 11),
    ): Pair<PokemonPastureBlockEntity, PokemonEntity> {
        val pasture = TestKit.placePasture(helper, rel, placer)
        TestKit.workMode(pasture, true)
        return pasture to TestKit.tether(helper, pasture, placer, "charmander level=10")
    }
}
