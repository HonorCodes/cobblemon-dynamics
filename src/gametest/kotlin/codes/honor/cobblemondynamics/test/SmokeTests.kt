package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.CobblemonDynamics
import codes.honor.cobblemondynamics.MOD_ID
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.neoforged.fml.ModList
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class SmokeTests {
    /** CD-REL-10: the GameTest run loads the pinned Cobblemon, Create and AE2 versions. */
    @GameTest(template = "floor_9x5x9")
    fun cdRel10PinnedModsLoad(helper: GameTestHelper) {
        val expected = mapOf("cobblemon" to "1.8.4", "create" to "6.0.10", "ae2" to "19.2.19", MOD_ID to "1.0.0")
        for ((modId, prefix) in expected) {
            val container = ModList.get().getModContainerById(modId).orElse(null)
            helper.assertTrue(container != null, "$modId is not loaded")
            val version = container!!.modInfo.version.toString()
            CobblemonDynamics.LOGGER.info("GameTest run loads {} {}", modId, version)
            helper.assertTrue(version.startsWith(prefix), "$modId is $version, expected $prefix")
        }
        helper.succeed()
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 200)
    fun smokePastureTethersAPokemon(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        helper.assertTrue(pasture.ownerId == owner.uuid, "ownerId is not the placer")
        val entity = TestKit.tether(helper, pasture, owner, "charmander level=10")
        helper.runAfterDelay(20) {
            helper.assertTrue(entity.tethering?.pasturePos == pasture.blockPos, "entity is not tethered to the pasture")
            helper.assertTrue(pasture.tetheredPokemon.any { it.pokemonId == entity.pokemon.uuid }, "pasture lacks the Pokémon")
            helper.succeed()
        }
    }
}
