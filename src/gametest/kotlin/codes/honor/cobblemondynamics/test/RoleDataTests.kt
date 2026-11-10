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
import net.minecraft.resources.ResourceLocation
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
    private val generatorId = id("generator")

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

    /**
     * The server config disables the Stoker and the Generator by id. For 400 ticks a Charmander beside a furnace with input and a
     * Pikachu beside the test battery claim nothing, and neither role is active (CD-CFG-05).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 500, batch = "roles_cfg05")
    fun cdCfg05DisabledRoleGetsNoClaims(helper: GameTestHelper) {
        val settings = WorkEngineSettings.current
        WorkEngineSettings.current = settings.copy(disabledRoles = setOf(stokerId, generatorId))
        RoleRegistry.rebuild()
        TestKit.afterTest(helper) {
            WorkEngineSettings.current = settings
            RoleRegistry.rebuild()
        }
        val (owner, pasture) = pasture(helper)
        TestKit.furnace(helper, BlockPos(7, 1, 3), ItemStack(Items.RAW_IRON, 8))
        TestKit.testBattery(helper, BlockPos(7, 1, 11))
        val fire = TestKit.tether(helper, pasture, owner, "charmander level=10")
        val pikachu = TestKit.tether(helper, pasture, owner, "pikachu level=50")
        TestKit.moveTo(helper, fire, BlockPos(7, 1, 5))
        TestKit.moveTo(helper, pikachu, BlockPos(7, 2, 9))
        for (id in listOf(stokerId, generatorId)) helper.assertTrue(RoleRegistry.current.get(id) == null, "the disabled role $id is active")
        helper.onEachTick {
            helper.assertTrue(TestKit.claimOf(helper, fire) == null, "a Charmander claimed a furnace of a disabled role")
            helper.assertTrue(TestKit.claimOf(helper, pikachu) == null, "CD-CFG-05: a Pikachu claimed a battery of the disabled Generator")
        }
        helper.runAtTickTime(400) { helper.succeed() }
    }

    /**
     * Through the reload path, as `/reload` with a data pack: the Generator's level-100 rate becomes 2,500, and a Lv100 Pikachu gives
     * the test battery exactly 2,500 FE on each tick of work (CD-CFG-03). Then the rate becomes 20,000, and a Lv100 Zapdos, a
     * legendary, gives its battery 40,000 FE on each tick: no cap holds it below the role file (CD-CFG-13).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 800, batch = "roles_gen")
    fun cdCfg03Cfg13DatapackSetsTheGeneratorRates(helper: GameTestHelper) {
        val generator = RoleRegistry.current.get(generatorId)?.definition ?: throw GameTestAssertException("no Generator role")
        fun withMax(max: Int): Map<ResourceLocation, RoleDefinition> {
            val params = generator.params.deepCopy().apply { addProperty("max_fe_per_tick", max) }
            return mapOf(generatorId to generator.copy(params = params))
        }
        val previous = RoleRegistry.applyForTest(withMax(LOW_MAX))
        TestKit.afterTest(helper) { RoleRegistry.applyForTest(previous) }
        val (owner, pasture) = pasture(helper)
        val pikachuBattery = TestKit.testBattery(helper, BlockPos(3, 1, 3))
        TestKit.tether(helper, pasture, owner, "pikachu level=100")
        var zapdosBattery: TestBatteryBlockEntity? = null
        var raisedAt = -1L
        helper.onEachTick {
            if (raisedAt < 0) {
                val offers = pikachuBattery.offers
                helper.assertTrue(offers.all { it.offered == LOW_MAX && it.accepted == LOW_MAX }, "CD-CFG-03: the offers are $offers")
                if (offers.size < 20) return@onEachTick
                RoleRegistry.applyForTest(withMax(HIGH_MAX))
                zapdosBattery = TestKit.testBattery(helper, BlockPos(11, 1, 3))
                TestKit.tether(helper, pasture, owner, "zapdos level=100")
                raisedAt = helper.level.gameTime
                return@onEachTick
            }
            val legendary = zapdosBattery!!.offers
            val wrong = legendary.firstOrNull { it.offered != 2 * HIGH_MAX || it.accepted != 2 * HIGH_MAX }
            helper.assertTrue(wrong == null, "CD-CFG-13: the Lv100 Zapdos offered $wrong")
            val raised = pikachuBattery.offers.filter { it.tick > raisedAt }
            helper.assertTrue(raised.all { it.offered == HIGH_MAX }, "after the second reload the Pikachu offered $raised")
            if (legendary.size >= 20) helper.succeed()
        }
    }

    private companion object {
        const val LOW_MAX = 2_500
        const val HIGH_MAX = 20_000
    }
}
