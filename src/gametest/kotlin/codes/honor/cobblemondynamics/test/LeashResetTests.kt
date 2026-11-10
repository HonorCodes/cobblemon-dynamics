package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.Feedback
import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.leash.BindingStore
import codes.honor.cobblemondynamics.registry.DynamicsAttachments
import com.cobblemon.mod.common.Cobblemon
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class LeashResetTests {
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
}
