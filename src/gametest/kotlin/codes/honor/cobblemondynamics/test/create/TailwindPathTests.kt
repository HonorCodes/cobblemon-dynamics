package codes.honor.cobblemondynamics.test.create

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.ai.BrainBridge
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.test.TestKit
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/**
 * A Tailwind worker that flies over its own pasture to its spot. The pasture block's screen sits 1/16 back from its frame, in a recess
 * 0.5 high under the frame's top bar, here on the pasture's south side, where TestKit tethers. The Pidgey, 0.49 high at its species'
 * size, starts 3 blocks south of the pasture. Its spot is the cell south of a windmill bearing north of the pasture, and its path climbs
 * over the pasture from the screen's side. In flight it pushed into the recess, where the top bar stops it from rising and the screen
 * from going on. Cobblemon's navigation reports no stuck worker in flight, and the middle of its cell lies inside the screen, so it
 * hovered there until its travel timed out (CD-ASG-17), in 20 of 20 runs of each test. The engine now finds the stalled travel and
 * puts the worker on top of the block in its cell, and the Pidgey Works long before the timeout (CD-ASG-11).
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class TailwindPathTests {
    /** The claim comes in the tether's tick, as in the compat run that found the stall. */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdAsg11PidgeyClaimedAtItsTetherFliesOverThePasture(helper: GameTestHelper) = run(helper, holdUntilClaim = false)

    /** The Pidgey stands where Cobblemon tethers it until its claim, with its wander paused. */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdAsg11PidgeyHeldSouthOfThePastureFliesOverIt(helper: GameTestHelper) = run(helper, holdUntilClaim = true)

    private fun run(helper: GameTestHelper, holdUntilClaim: Boolean) {
        val bearing = CreateKit.windmill(helper, BEARING, sails = 9)
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, PASTURE, owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        val pidgey = tetherPidgey(helper, pasture, owner)
        val start = helper.absoluteVec(START)
        val tetheredAt = pidgey.position()
        helper.assertTrue(tetheredAt.distanceTo(start) < 0.01, "Cobblemon tethered the Pidgey at $tetheredAt, not at $start")
        if (!holdUntilClaim) bind(helper, owner, pidgey)
        var ticks = 0
        var claimedAt = -1L
        helper.onEachTick {
            ticks++
            if (ticks == 2) bearing.assemble()
            val worker = TestKit.workerOf(helper, pidgey)
            if (worker == null || TestKit.claimOf(helper, pidgey) == null) {
                if (holdUntilClaim) {
                    hold(pidgey, start)
                    if (ticks == BIND_TICK) bind(helper, owner, pidgey)
                }
                helper.assertTrue(ticks <= BIND_TICK + CLAIM_TICKS, "the Pidgey has no claim $CLAIM_TICKS ticks after its binding")
                return@onEachTick
            }
            if (claimedAt < 0) {
                claimedAt = worker.claimTick
                val spot = worker.spot
                helper.assertTrue(spot == helper.absolutePos(BEARING.south()), "the Pidgey's spot is $spot, not south of the bearing")
            }
            val since = helper.level.gameTime - claimedAt
            if (worker.status == WorkerStatus.WORKING) {
                helper.succeed()
            } else {
                val at = pidgey.position().subtract(Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO)))
                helper.assertTrue(
                    since < ARRIVAL_LIMIT,
                    "CD-ASG-11: the Pidgey is ${worker.status} at $at, flying ${pidgey.isFlying()}, $since ticks after its claim",
                )
            }
        }
    }

    private fun bind(helper: GameTestHelper, owner: ServerPlayer, pidgey: PokemonEntity) {
        if (TestKit.bind(helper, owner, pidgey, BEARING) == null) throw GameTestAssertException("the leash did not bind the Pidgey")
    }

    /** Pauses the wander of [entity] and puts it back on [at] whenever it drifts. Its velocity is left alone, so it stays on the floor. */
    private fun hold(entity: PokemonEntity, at: Vec3) {
        BrainBridge.pauseWander(entity, HOLD_PAUSE_TICKS)
        if (Vec3(entity.x - at.x, 0.0, entity.z - at.z).length() > HOLD_DRIFT) entity.teleportTo(at.x, at.y, at.z)
    }

    /** Cobblemon rolls each size within about 5%, and a Pidgey over 0.5 high does not fit the screen's recess, so its size is fixed. */
    private fun tetherPidgey(helper: GameTestHelper, pasture: PokemonPastureBlockEntity, owner: ServerPlayer): PokemonEntity {
        val pokemon = TestKit.pokemon("pidgey level=50")
        pokemon.scaleModifier = 1f
        Cobblemon.storage.getPC(owner).add(pokemon)
        if (!pasture.tether(owner, pokemon, Direction.SOUTH)) throw GameTestAssertException("tether failed for the Pidgey")
        return TestKit.pasturedEntity(helper, pasture, pokemon.uuid) ?: throw GameTestAssertException("no Pidgey after the tether")
    }

    private companion object {
        /** The pasture's top half faces north, so its screen's recess is on its south side. */
        val PASTURE = BlockPos(7, 1, 9)

        /** A bearing that faces up, north of the pasture. The Pidgey's spot is the cell beside it nearest to the Pidgey, south of it. */
        val BEARING = BlockPos(7, 2, 5)

        /** Where Cobblemon tethers a Pokémon of the Pidgey's width south of the pasture: 3 blocks out, on the floor. */
        val START = Vec3(7.5, 2.0, 12.5)

        const val BIND_TICK = 5
        const val CLAIM_TICKS = 40

        /** The Pidgey Worked 204 to 257 ticks after its claim in 42 runs. The limit stays well within the travel timeout of 600 ticks. */
        const val ARRIVAL_LIMIT = 400L

        const val HOLD_DRIFT = 0.02
        const val HOLD_PAUSE_TICKS = 2L
    }
}
