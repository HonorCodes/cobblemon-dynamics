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
import net.minecraft.gametest.framework.AfterBatch
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerLevel
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

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

    /**
     * A Lv10 Charmander at 0% goes to Cobblemon's PC at game time T, and the engine saves it there Tired. Its saved entry then says
     * that it went in 18,000 ticks before it comes back: until it joins again, each tick moves `last_game_time` to 18,000 ticks
     * before the next tick. It joins through the PC at 50% ± 0.1% (CD-STA-11), plus the one tick of its join.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 300)
    fun cdSta11PcTimeRecoversAtBaseRate(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        val pc = Cobblemon.storage.getPC(owner)
        val id = TestKit.tether(helper, pasture, owner, "charmander level=10").pokemon.uuid
        val engine = TestKit.engine(helper)
        var step = "join"
        helper.onEachTick {
            when (step) {
                "join" -> {
                    val live = engine.worker(id)?.stamina ?: return@onEachTick
                    live.fraction = 0.0
                    live.tired = true
                    pasture.releasePokemon(id)
                    step = "released"
                }

                "released" -> {
                    if (engine.worker(id) != null) return@onEachTick
                    val pokemon = pc[id] ?: throw GameTestAssertException("the PC lost the Charmander")
                    val saved = StaminaStore.read(pokemon.persistentData)
                    helper.assertTrue(saved?.tired == true, "the PC's Charmander was saved as $saved, not Tired")
                    step = "away"
                }

                "away" -> {
                    val pokemon = pc[id] ?: throw GameTestAssertException("the PC lost the Charmander")
                    StaminaStore.write(pokemon.persistentData, Stamina(0.0, tired = true), helper.level.gameTime + 1 - AWAY_TICKS)
                    if (engine.worker(id) == null) {
                        if (PastureAdapter.tetheredIds(pasture).isEmpty() && !pasture.tether(owner, pokemon, Direction.SOUTH)) {
                            throw GameTestAssertException("the second tether failed")
                        }
                        return@onEachTick
                    }
                    val live = engine.worker(id)?.stamina ?: return@onEachTick
                    val expected = 0.5 + 1 / CAPACITY_TICKS
                    helper.assertTrue(abs(live.percent - 50.0) <= 0.1, "CD-STA-11: the Charmander joined at $live after 18,000 ticks away")
                    helper.assertTrue(
                        abs(live.fraction - expected) < 1e-9,
                        "CD-STA-11: the Charmander joined at $live, not ${expected * 100}%",
                    )
                    helper.succeed()
                }
            }
        }
    }

    /**
     * A Lv10 Charmander at 40% waits in the PC. An operator runs `/time set day` and `/time add 24000`: the day time moves, the game
     * time does not, and the saved entry and its load at this tick stay as they were. Tethered again, it joins with only the recovery
     * of the game ticks that passed, not of the 24,000 ticks that `/time add` skipped (CD-STA-12). The day time is global, so the test
     * has its own batch, which puts it back to the test mod's 6,000.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 300, batch = TIME_BATCH)
    fun cdSta12TimeCommandsChangeNothing(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val operator = TestKit.operator(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        val pc = Cobblemon.storage.getPC(owner)
        val id = TestKit.tether(helper, pasture, owner, "charmander level=10").pokemon.uuid
        val engine = TestKit.engine(helper)
        val settings = WorkEngineSettings.current
        var step = "join"
        var before: Stamina? = null
        var commandsAt = -1L
        helper.onEachTick {
            when (step) {
                "join" -> {
                    val live = engine.worker(id)?.stamina ?: return@onEachTick
                    live.fraction = START_STA12
                    pasture.releasePokemon(id)
                    step = "released"
                }

                "released" -> {
                    if (engine.worker(id) != null) return@onEachTick
                    val pokemon = pc[id] ?: throw GameTestAssertException("the PC lost the Charmander")
                    val now = helper.level.gameTime
                    val saved = StaminaStore.read(pokemon.persistentData)
                    val loaded = StaminaStore.load(pokemon, now, settings)
                    val dayTime = helper.level.dayTime
                    val commands = helper.level.server.commands
                    commands.performPrefixedCommand(operator.createCommandSourceStack(), "time set day")
                    helper.assertTrue(
                        helper.level.dayTime != dayTime,
                        "'/time set day' did not run: the day time is ${helper.level.dayTime}",
                    )
                    val setDay = helper.level.dayTime
                    commands.performPrefixedCommand(operator.createCommandSourceStack(), "time add 24000")
                    helper.assertTrue(helper.level.dayTime == setDay + 24_000, "'/time add 24000' did not run: ${helper.level.dayTime}")
                    helper.assertTrue(helper.level.gameTime == now, "the time commands moved the game time to ${helper.level.gameTime}")
                    val savedAfter = StaminaStore.read(pokemon.persistentData)
                    helper.assertTrue(savedAfter == saved, "CD-STA-12: the time commands changed the saved entry $saved to $savedAfter")
                    val loadedAfter = StaminaStore.load(pokemon, helper.level.gameTime, settings)
                    helper.assertTrue(
                        loadedAfter.fraction == loaded.fraction && loadedAfter.tired == loaded.tired,
                        "CD-STA-12: the PC's Charmander read $loaded before the time commands and $loadedAfter after them",
                    )
                    before = loaded
                    commandsAt = now
                    step = "away"
                }

                "away" -> {
                    if (engine.worker(id) == null) {
                        val pokemon = pc[id] ?: throw GameTestAssertException("the PC lost the Charmander")
                        if (PastureAdapter.tetheredIds(pasture).isEmpty() && !pasture.tether(owner, pokemon, Direction.SOUTH)) {
                            throw GameTestAssertException("the second tether failed")
                        }
                        return@onEachTick
                    }
                    val live = engine.worker(id)?.stamina ?: return@onEachTick
                    val most = before!!.fraction + (helper.level.gameTime - commandsAt + 1) / CAPACITY_TICKS
                    val ok = live.fraction >= before!!.fraction && live.fraction <= most + SLACK
                    helper.assertTrue(ok, "CD-STA-12: the Charmander joined at $live, from $before, allowed up to ${most * 100}%")
                    helper.succeed()
                }
            }
        }
    }

    @AfterBatch(batch = TIME_BATCH)
    fun dayTimeBack(level: ServerLevel) {
        level.server.allLevels.forEach { it.dayTime = DAY }
    }

    private companion object {
        const val TIMEOUT_TICKS = 300
        const val START = 0.37
        const val START_STA12 = 0.40
        const val CAPACITY_TICKS = 36_000.0
        const val SLACK = 1e-9
        const val AWAY_TICKS = 18_000L
        const val TIME_BATCH = "time_sta12"
        const val DAY = 6_000L
    }
}
