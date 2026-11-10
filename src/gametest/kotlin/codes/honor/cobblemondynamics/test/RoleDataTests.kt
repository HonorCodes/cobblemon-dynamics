package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.role.RoleDefinition
import codes.honor.cobblemondynamics.role.RoleRegistry
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/** Each test changes the global role set or config, so each runs alone in its own batch and puts the state back when it ends. */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class RoleDataTests {
    private val stokerId = id("stoker")

    private fun stoker(): RoleDefinition = RoleRegistry.current.get(stokerId)?.definition ?: throw GameTestAssertException("no Stoker role")

    /** Replaces the Stoker's types through the reload path, as `/reload` with a changed data pack does. */
    private fun reloadStoker(helper: GameTestHelper, types: Set<String>) {
        val previous = RoleRegistry.applyForTest(mapOf(stokerId to stoker().copy(types = types)))
        TestKit.afterTest(helper) { RoleRegistry.applyForTest(previous) }
    }

    private fun pasture(helper: GameTestHelper): Pair<ServerPlayer, PokemonPastureBlockEntity> {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        return owner to pasture
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 400, batch = "roles_cfg07")
    fun cdCfg07DatapackLetsWaterTypesStoke(helper: GameTestHelper) {
        reloadStoker(helper, setOf("fire", "water"))
        val (owner, pasture) = pasture(helper)
        TestKit.furnace(helper, BlockPos(7, 1, 3), ItemStack(Items.RAW_IRON, 8))
        val water = TestKit.tether(helper, pasture, owner, "squirtle level=10")
        helper.succeedWhen { helper.assertTrue(TestKit.claimOf(helper, water) != null, "a Water-type did not claim the furnace") }
    }

    /** A Squirtle shows that the new types apply, and a Charmander that the old ones no longer do. */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 300, batch = "roles_cfg08")
    fun cdCfg08ReloadAppliesChangedTypes(helper: GameTestHelper) {
        reloadStoker(helper, setOf("water"))
        val (owner, pasture) = pasture(helper)
        TestKit.furnace(helper, BlockPos(3, 1, 3), ItemStack(Items.RAW_IRON, 8))
        TestKit.furnace(helper, BlockPos(11, 1, 3), ItemStack(Items.RAW_IRON, 8))
        val fire = TestKit.tether(helper, pasture, owner, "charmander level=10")
        val water = TestKit.tether(helper, pasture, owner, "squirtle level=10")
        helper.onEachTick { helper.assertTrue(TestKit.claimOf(helper, fire) == null, "the old types still apply: a Fire-type claimed") }
        helper.runAtTickTime(200) {
            helper.assertTrue(TestKit.claimOf(helper, water) != null, "the new types do not apply: the Water-type holds no claim")
            helper.succeed()
        }
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 500, batch = "roles_cfg05")
    fun cdCfg05DisabledRoleGetsNoClaims(helper: GameTestHelper) {
        val settings = WorkEngineSettings.current
        WorkEngineSettings.current = settings.copy(disabledRoles = setOf(stokerId))
        RoleRegistry.rebuild()
        TestKit.afterTest(helper) {
            WorkEngineSettings.current = settings
            RoleRegistry.rebuild()
        }
        val (owner, pasture) = pasture(helper)
        TestKit.furnace(helper, BlockPos(7, 1, 3), ItemStack(Items.RAW_IRON, 8))
        val fire = TestKit.tether(helper, pasture, owner, "charmander level=10")
        TestKit.moveTo(helper, fire, BlockPos(7, 1, 5))
        helper.onEachTick { helper.assertTrue(TestKit.claimOf(helper, fire) == null, "a Charmander claimed a furnace of a disabled role") }
        helper.runAtTickTime(400) { helper.succeed() }
    }
}
