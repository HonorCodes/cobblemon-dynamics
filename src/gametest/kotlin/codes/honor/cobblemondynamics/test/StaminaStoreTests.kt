package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.pasture.PastureAdapter
import codes.honor.cobblemondynamics.registry.DynamicsAttachments
import codes.honor.cobblemondynamics.stamina.Stamina
import codes.honor.cobblemondynamics.stamina.StaminaStore
import codes.honor.cobblemondynamics.stamina.StaminaView
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.activestate.InactivePokemonState
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class StaminaStoreTests {
    /**
     * A new Lv10 Charmander joins at 100% (CD-STA-05), then is set to 37% and goes through Cobblemon's real PC and party, a new
     * tether, a simulated restart with an NBT round trip of the PC's instance, and Cobblemon's instance swap (CD-STA-04). It has no
     * furnace, so it never works, and its value may only rise by the base-rate recovery of the ticks since it was set: 1 / 36,000
     * per tick. The restart drops the whole engine, so this test runs alone in its own batch.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = TIMEOUT_TICKS, batch = "restart_sta04")
    fun cdSta04And05StaminaStartsFullAndFollowsThePokemon(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        val pc = Cobblemon.storage.getPC(owner)
        val party = Cobblemon.storage.getParty(owner)
        var entity: PokemonEntity = TestKit.tether(helper, pasture, owner, "charmander level=10")
        val id = entity.pokemon.uuid
        val engine = TestKit.engine(helper)
        val access = helper.level.registryAccess()
        var step = "join"
        var setAt = 0L

        /** [value] may be no lower than 37% and no higher than 37% plus the recovery of the ticks since [setAt]. */
        fun checkValue(value: Stamina, where: String) {
            val elapsed = helper.level.gameTime - setAt
            val most = START + elapsed / CAPACITY_TICKS
            val ok = value.fraction >= START - SLACK && value.fraction <= most + SLACK && !value.tired
            helper.assertTrue(ok, "$where: $value, allowed ${START * 100}% to ${most * 100}% after $elapsed ticks")
        }

        helper.runAtTickTime(TIMEOUT_TICKS - 1L) {
            val tethered = PastureAdapter.tetheredIds(pasture).contains(id)
            val worker = engine.worker(id)?.status
            helper.fail("stuck at '$step': worker $worker, entity removed ${entity.isRemoved}, tethered $tethered, in PC ${pc[id] != null}")
        }
        helper.onEachTick {
            when (step) {
                "join" -> {
                    val live = engine.worker(id)?.stamina ?: return@onEachTick
                    helper.assertTrue(live.fraction == 1.0 && !live.tired, "CD-STA-05: a new worker reads $live")
                    val view = entity.getExistingDataOrNull(DynamicsAttachments.STAMINA_VIEW)
                    helper.assertTrue(view == StaminaView(100, WorkerStatus.IDLE), "CD-STA-05: the new worker's view is $view")
                    live.fraction = START
                    setAt = helper.level.gameTime
                    pasture.releasePokemon(id)
                    step = "released"
                }

                "released" -> {
                    // The engine drops the worker when Cobblemon discards its entity or at the next poll, and flushes it then.
                    if (engine.worker(id) != null) return@onEachTick
                    val pokemon = pc[id] ?: throw GameTestAssertException("the PC lost the Charmander")
                    checkValue(StaminaStore.load(pokemon, helper.level.gameTime + 1, WorkEngineSettings.current), "in the PC")
                    pc.remove(pokemon)
                    party.add(pokemon)
                    checkValue(StaminaStore.load(pokemon, helper.level.gameTime + 1, WorkEngineSettings.current), "in the party")
                    party.remove(pokemon)
                    pc.add(pokemon)
                    if (!pasture.tether(owner, pokemon, Direction.SOUTH)) throw GameTestAssertException("the second tether failed")
                    entity = TestKit.pasturedEntity(helper, pasture, id)
                        ?: throw GameTestAssertException("no entity after the second tether")
                    step = "back"
                }

                "back" -> {
                    val live = engine.worker(id)?.stamina ?: return@onEachTick
                    checkValue(live, "after the PC, the party and a new tether")
                    // A real stop flushes every worker; then the PC loads from disk, which builds new Pokémon instances.
                    TestKit.simulateRestart(helper)
                    val old = pc[id] ?: throw GameTestAssertException("the PC lost the Charmander before the restart")
                    val fresh = Pokemon.loadFromNBT(access, old.saveToNBT(access))
                    checkValue(StaminaStore.load(fresh, helper.level.gameTime + 1, WorkEngineSettings.current), "after the restart")
                    // The PC now holds the new instance. Cobblemon gives a pastured Pokémon a SentOutState, and PCStore.remove
                    // recalls it, which would discard the pasture entity, so the old instance goes inactive first.
                    old.state = InactivePokemonState()
                    pc.remove(old)
                    pc.add(fresh)
                    // Cobblemon's 20-tick swap to the PC's instance, which runs only for an owner in the level's player list.
                    entity.pokemon = fresh
                    step = "swapped"
                }

                "swapped" -> {
                    val live = engine.worker(id)?.stamina ?: return@onEachTick
                    helper.assertTrue(engine.pokemonOf(id) === pc[id], "the engine does not hold the PC's instance")
                    checkValue(live, "after the restart and the instance swap")
                    helper.succeed()
                }
            }
        }
    }

    private companion object {
        const val TIMEOUT_TICKS = 300
        const val START = 0.37
        const val CAPACITY_TICKS = 36_000.0
        const val SLACK = 1e-9
    }
}
