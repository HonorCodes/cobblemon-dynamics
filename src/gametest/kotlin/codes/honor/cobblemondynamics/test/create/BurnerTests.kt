package codes.honor.cobblemondynamics.test.create

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.ai.BrainBridge
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.test.TestKit
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.experience.SidemodExperienceSource
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.simibubi.create.AllBlocks
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/**
 * The Superheater on real Blaze Burners, each filled, with no fuel, and with a basin or a fluid tank above. Below level 50 it keeps a
 * burner kindled, from level 50 it superheats it, and a Fire-type never works a burner (the approver's ruling, 2026-11-10).
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class BurnerTests {
    /** A Superheater below level 50, which keeps a burner `KINDLED` (CD-BRN-01). */
    private val lowDragon = "dratini level=10"

    private class Scene(val owner: ServerPlayer, val pasture: PokemonPastureBlockEntity)

    private val burnerRel = BlockPos(7, 2, 5)
    private val aboveRel = BlockPos(7, 3, 5)

    private fun scene(helper: GameTestHelper): Scene {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 9), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        return Scene(owner, pasture)
    }

    /** How many ticks [entity] has worked [burner] for, or null while it does not. */
    private fun workedTicks(helper: GameTestHelper, entity: PokemonEntity, burner: BlazeBurnerBlockEntity): Long? {
        val claim = TestKit.claimOf(helper, entity)
        val worker = TestKit.workerOf(helper, entity)
        if (claim?.kind != ClaimKind.WORK || claim.key != TestKit.keyOf(helper, burner.blockPos)) return null
        if (worker?.status != WorkerStatus.WORKING) return null
        return helper.level.gameTime - worker.statusSince
    }

    /**
     * CD-BRN-01, then CD-BRN-06 on the same burner: a Lv10 Dratini holds it at `KINDLED` 1 tick after Working starts, and the burner
     * reads the heat of its own fuel 2 ticks after the release.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdBrn01Brn06KindledBelowLevel50SmoulderingAfterRelease(helper: GameTestHelper) {
        val scene = scene(helper)
        val burner = CreateKit.filledBurner(helper, burnerRel)
        CreateKit.basin(helper, aboveRel)
        val dragon = TestKit.tether(helper, scene.pasture, scene.owner, lowDragon)
        var releasedAt = -1L
        helper.onEachTick {
            if (releasedAt < 0) {
                val worked = workedTicks(helper, dragon, burner) ?: return@onEachTick
                if (worked < 1) return@onEachTick
                val heat = CreateKit.heatAt(helper, burnerRel)
                helper.assertTrue(heat == HeatLevel.KINDLED, "the burner reads $heat $worked ticks after a Lv10 Superheater starts")
                TestKit.release(helper, dragon, burner.blockPos)
                releasedAt = helper.tick
            } else if (helper.tick - releasedAt >= 2) {
                val heat = CreateKit.heatAt(helper, burnerRel)
                helper.assertTrue(heat == HeatLevel.SMOULDERING, "the burner reads $heat 2 ticks after the release")
                helper.succeed()
            }
        }
    }

    /** CD-BRN-05: a boiler on a burner that a Lv10 Dratini works counts 1 heat from that burner. */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdBrn05BoilerCountsOneHeatBelowLevel50(helper: GameTestHelper) {
        val scene = scene(helper)
        val burner = CreateKit.filledBurner(helper, burnerRel)
        CreateKit.fluidTank(helper, aboveRel)
        val dragon = TestKit.tether(helper, scene.pasture, scene.owner, lowDragon)
        helper.succeedWhen {
            val worked = workedTicks(helper, dragon, burner)
            helper.assertTrue(worked != null && worked >= 1, "the Dratini does not work the burner under the boiler")
            assertBoiler(helper, HeatLevel.KINDLED, 1)
        }
    }

    /**
     * CD-SUP-02: a Lv10 Charmander beside a filled burner with a basin above. The Work Leash finds no role for it, and in 400 ticks it
     * never claims the burner. Then a Lv10 Dratini joins the pasture and claims the burner, which shows that the burner was a target.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 900)
    fun cdSup02FireTypeNeverClaimsABlazeBurner(helper: GameTestHelper) {
        val scene = scene(helper)
        val burner = CreateKit.filledBurner(helper, burnerRel)
        CreateKit.basin(helper, aboveRel)
        val fire = TestKit.tether(helper, scene.pasture, scene.owner, "charmander level=10")
        TestKit.moveTo(helper, fire, BlockPos(7, 2, 7))
        val binding = TestKit.bind(helper, scene.owner, fire, burnerRel)
        val reply = TestKit.lastMessage(scene.owner)
        helper.assertTrue(binding == null, "CD-SUP-02: the leash bound the Charmander to the burner: $binding")
        helper.assertTrue(reply == "No role fits", "CD-SUP-02: the click on the burner answered '$reply'")
        val key = TestKit.keyOf(helper, burner.blockPos)
        var dragon: PokemonEntity? = null
        helper.onEachTick {
            helper.assertFalse(TestKit.claimOf(helper, fire)?.key == key, "CD-SUP-02: the Charmander claimed the Blaze Burner")
            val superheater = dragon
            if (superheater == null) {
                if (helper.tick >= 400) dragon = TestKit.tether(helper, scene.pasture, scene.owner, lowDragon)
            } else if (TestKit.claimOf(helper, superheater)?.key == key) {
                helper.succeed()
            }
        }
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdSup01Lv50Seething(helper: GameTestHelper) {
        val scene = scene(helper)
        val burner = CreateKit.filledBurner(helper, burnerRel)
        CreateKit.basin(helper, aboveRel)
        val dragon = TestKit.tether(helper, scene.pasture, scene.owner, "dragonair level=50")
        helper.onEachTick {
            val worked = workedTicks(helper, dragon, burner) ?: return@onEachTick
            if (worked < 1) return@onEachTick
            val heat = CreateKit.heatAt(helper, burnerRel)
            helper.assertTrue(heat == HeatLevel.SEETHING, "the burner reads $heat $worked ticks after a Lv50 Superheater starts")
            helper.succeed()
        }
    }

    /**
     * CD-ASG-11, CD-SUP-01: a Lv50 Dragonair 1 to 2 blocks wide stands in its work spot's cell when its claim starts, at the cell's corner
     * farthest from the corner where navigation stops such a body. Cobblemon's walk to the spot ends once the Dragonair stands in that
     * cell, so it has arrived: it Works at once instead of waiting 600 ticks for its travel timeout (the flake of cdSup01Lv50Seething).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 400)
    fun cdAsg11WideWorkerInItsSpotCellArrives(helper: GameTestHelper) {
        val scene = scene(helper)
        val burner = CreateKit.filledBurner(helper, burnerRel)
        CreateKit.basin(helper, aboveRel)
        val dragon = tetherDragonair(helper, scene, WIDE_SCALE)
        helper.assertTrue(dragon.bbWidth >= 1f, "the Dragonair is ${dragon.bbWidth} wide, not 1 to 2 blocks")
        // The basin leaves a body this wide no room beside the burner, so its spot is the cell 2 blocks south of it.
        val cell = BlockPos(7, 2, 7)
        val at = helper.absoluteVec(Vec3(cell.x + 0.05, cell.y.toDouble(), cell.z + 0.05))
        var claimedAt = -1L
        helper.onEachTick {
            if (holdBeforeClaim(helper, dragon, at)) return@onEachTick
            val worker = TestKit.workerOf(helper, dragon) ?: throw GameTestAssertException("the Dragonair is no worker")
            if (claimedAt < 0) {
                // An arrival in the claim's own tick snaps the Dragonair to the spot's center before this check runs, so the hold, which
                // puts it back within 0.02 of `at` on every tick before the claim, is what keeps it in the cell's far corner.
                claimedAt = worker.claimTick
                helper.assertTrue(worker.spot == helper.absolutePos(cell), "the Dragonair's spot is ${worker.spot}, not its own cell")
            }
            val worked = workedTicks(helper, dragon, burner)
            if (worked == null) {
                val ticks = helper.level.gameTime - claimedAt
                helper.assertTrue(ticks < 2, "CD-ASG-11: the Dragonair in its spot's cell is ${worker.status} $ticks ticks after its claim")
            } else if (worked >= 1) {
                val late = worker.statusSince - claimedAt
                helper.assertTrue(late <= 1, "CD-ASG-11: the Dragonair started Working $late ticks after its claim")
                val heat = CreateKit.heatAt(helper, burnerRel)
                helper.assertTrue(heat == HeatLevel.SEETHING, "the burner reads $heat $worked ticks after a Lv50 Superheater starts")
                helper.succeed()
            }
        }
    }

    /**
     * CD-ASG-11, CD-SUP-01: a Lv50 Dragonair narrower than a block stands pressed against the pasture block's south face when its claim
     * starts. Cobblemon starts each path in the cell at the lowest corner of the mob's box, here the pasture block's cell, which the
     * Dragonair can never enter, so its navigation reports it stuck. The engine moves it to the middle of its own cell, it walks around
     * the pasture block, and it Works long before the travel timeout of 600 ticks (the flake of cdSup01Lv50Seething).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 600)
    fun cdAsg11WorkerPressedAgainstThePastureReachesItsSpot(helper: GameTestHelper) {
        val scene = scene(helper)
        val burner = CreateKit.filledBurner(helper, burnerRel)
        CreateKit.basin(helper, aboveRel)
        val dragon = tetherDragonair(helper, scene, NARROW_SCALE)
        helper.assertTrue(dragon.bbWidth < 1f, "the Dragonair is ${dragon.bbWidth} wide, not narrower than a block")
        // The pasture block's top half fills its cell from the north to its south face, above a plate that nearly reaches the cell's edge.
        val top = scene.pasture.blockPos.above()
        val face = helper.level.getBlockState(top).getCollisionShape(helper.level, top).toAabbs().filter { it.maxY > PLATE_TOP }
            .maxOf { it.maxZ } + top.z
        val at = Vec3(top.x + 0.5, top.y + PLATE_TOP, face + dragon.bbWidth / 2.0 + 0.002)
        var claimedAt = -1L
        helper.onEachTick {
            if (holdBeforeClaim(helper, dragon, at)) return@onEachTick
            val worker = TestKit.workerOf(helper, dragon) ?: throw GameTestAssertException("the Dragonair is no worker")
            if (claimedAt < 0) {
                claimedAt = worker.claimTick
                val box = dragon.boundingBox
                val corner = BlockPos(Mth.floor(box.minX), Mth.floor(box.minY + 0.5), Mth.floor(box.minZ))
                helper.assertTrue(corner == top, "the corner of the Dragonair's box is in $corner, not in the pasture block's cell")
            }
            val worked = workedTicks(helper, dragon, burner)
            if (worked == null) {
                val ticks = helper.level.gameTime - claimedAt
                helper.assertTrue(ticks < PRESSED_LIMIT, "the pressed Dragonair is ${worker.status} $ticks ticks after its claim")
            } else if (worked >= 1) {
                val heat = CreateKit.heatAt(helper, burnerRel)
                helper.assertTrue(heat == HeatLevel.SEETHING, "the burner reads $heat $worked ticks after a Lv50 Superheater starts")
                helper.succeed()
            }
        }
    }

    /** A Lv50 Dragonair whose size is [scale] times its species' size, so its width does not vary with Cobblemon's own size roll. */
    private fun tetherDragonair(helper: GameTestHelper, scene: Scene, scale: Float): PokemonEntity {
        val pokemon = TestKit.pokemon("dragonair level=50")
        pokemon.scaleModifier = scale
        Cobblemon.storage.getPC(scene.owner).add(pokemon)
        if (!scene.pasture.tether(scene.owner, pokemon, Direction.SOUTH)) throw GameTestAssertException("tether failed for the Dragonair")
        return TestKit.pasturedEntity(helper, scene.pasture, pokemon.uuid) ?: throw GameTestAssertException("no Dragonair after tether")
    }

    /**
     * Until [entity] holds a claim, pauses its wander, puts it back on [at] whenever it drifts, and returns true. Its velocity is left
     * alone, so it stays on the ground, where its navigation can plan the path of its claim.
     */
    private fun holdBeforeClaim(helper: GameTestHelper, entity: PokemonEntity, at: Vec3): Boolean {
        if (TestKit.claimOf(helper, entity) != null) return false
        BrainBridge.pauseWander(entity, HOLD_PAUSE_TICKS)
        if (Vec3(entity.x - at.x, 0.0, entity.z - at.z).length() > HOLD_DRIFT) entity.teleportTo(at.x, at.y, at.z)
        return true
    }

    /**
     * A Lv49 Dragonair holds the burner under a boiler at KINDLED with 1 heat for 20 ticks (CD-SUP-04); then it levels up through
     * Cobblemon, and the burner turns SEETHING with 2 heat (CD-SUP-04, CD-SUP-03).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_000)
    fun cdSup03Sup04Lv49ThenLv50(helper: GameTestHelper) {
        val scene = scene(helper)
        val burner = CreateKit.filledBurner(helper, burnerRel)
        CreateKit.fluidTank(helper, aboveRel)
        val dragon = TestKit.tether(helper, scene.pasture, scene.owner, "dragonair level=49")
        var kindledSince = -1L
        var leveledAt = -1L
        helper.onEachTick {
            if (leveledAt < 0) {
                val worked = workedTicks(helper, dragon, burner) ?: return@onEachTick
                if (worked < 1) return@onEachTick
                assertBoiler(helper, HeatLevel.KINDLED, 1)
                if (kindledSince < 0) kindledSince = helper.tick
                if (helper.tick - kindledSince < 20) return@onEachTick
                val stored = Cobblemon.storage.getPC(scene.owner)[dragon.pokemon.uuid]
                    ?: throw GameTestAssertException("no Dragonair in the PC")
                stored.levelUp(SidemodExperienceSource(MOD_ID))
                helper.assertTrue(stored.level == 50, "the Dragonair is level ${stored.level} after its level-up")
                leveledAt = helper.tick
            } else if (CreateKit.heatAt(helper, burnerRel) != HeatLevel.SEETHING) {
                val heat = CreateKit.heatAt(helper, burnerRel)
                helper.assertTrue(helper.tick - leveledAt < 5, "the burner reads $heat ${helper.tick - leveledAt} ticks after Lv50")
            } else {
                assertBoiler(helper, HeatLevel.SEETHING, 2)
                helper.succeed()
            }
        }
    }

    /**
     * An empty Blaze Burner with a basin above, and a Lv10 Dratini beside it for 400 ticks: no claim, and the Work Leash finds no role
     * for it (CD-BRN-02). Then the burner is filled, and the Dratini claims it, which shows that the empty one was in its reach.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 700)
    fun cdBrn02EmptyBurnerIsNeverClaimed(helper: GameTestHelper) {
        val scene = scene(helper)
        helper.setBlock(burnerRel, AllBlocks.BLAZE_BURNER.defaultState.setValue(BlazeBurnerBlock.HEAT_LEVEL, HeatLevel.NONE))
        CreateKit.basin(helper, aboveRel)
        val burner = helper.absolutePos(burnerRel)
        val dragon = TestKit.tether(helper, scene.pasture, scene.owner, lowDragon)
        TestKit.moveTo(helper, dragon, BlockPos(7, 2, 7))
        val binding = TestKit.bind(helper, scene.owner, dragon, burnerRel)
        val reply = TestKit.lastMessage(scene.owner)
        helper.assertTrue(binding == null, "CD-BRN-02: the leash bound the Dratini to the empty burner: $binding")
        helper.assertTrue(reply == "No role fits", "CD-BRN-02: the click on the empty burner answered '$reply'")
        var filled = false
        helper.onEachTick {
            val claim = TestKit.claimOf(helper, dragon)
            if (!filled) {
                helper.assertFalse(claim?.key == TestKit.keyOf(helper, burner), "CD-BRN-02: the Dratini claimed the empty burner")
                if (helper.tick < 400) return@onEachTick
                CreateKit.filledBurner(helper, burnerRel)
                filled = true
                return@onEachTick
            }
            if (claim?.key == TestKit.keyOf(helper, burner)) helper.succeed()
        }
    }

    /** The basin above a worked burner is removed, and its worker is in Standby within 20 ticks (CD-BRN-03). */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdBrn03NoBasinMeansStandby(helper: GameTestHelper) {
        val scene = scene(helper)
        val burner = CreateKit.filledBurner(helper, burnerRel)
        CreateKit.basin(helper, aboveRel)
        val dragon = TestKit.tether(helper, scene.pasture, scene.owner, lowDragon)
        var removedAt = -1L
        helper.onEachTick {
            val worker = TestKit.workerOf(helper, dragon) ?: return@onEachTick
            if (removedAt < 0) {
                val worked = workedTicks(helper, dragon, burner) ?: return@onEachTick
                if (worked < 1) return@onEachTick
                helper.setBlock(aboveRel, Blocks.AIR)
                removedAt = helper.tick
            } else if (worker.status == WorkerStatus.STANDBY) {
                helper.assertTrue(
                    TestKit.claimOf(helper, dragon)?.key == TestKit.keyOf(helper, burner.blockPos),
                    "the worker left its burner",
                )
                helper.succeed()
            } else {
                val waited = helper.tick - removedAt
                helper.assertTrue(waited < 20, "CD-BRN-03: $waited ticks after the basin went the worker is ${worker.status}")
            }
        }
    }

    /**
     * Two burners, each with 1,000 ticks of its own normal fuel and a basin above. A Lv10 Dratini bound to the first heats it for 400
     * ticks, and the two remaining burn times are equal: the worker neither burns nor adds fuel (CD-BRN-04).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_000)
    fun cdBrn04FuelBurnsDownAsNormal(helper: GameTestHelper) {
        val scene = scene(helper)
        val otherRel = BlockPos(11, 2, 5)
        val worked = fueledBurner(helper, burnerRel)
        val unworked = fueledBurner(helper, otherRel)
        CreateKit.basin(helper, aboveRel)
        CreateKit.basin(helper, otherRel.above())
        val dragon = TestKit.tether(helper, scene.pasture, scene.owner, lowDragon)
        val binding = TestKit.bind(helper, scene.owner, dragon, burnerRel)
        helper.assertTrue(binding?.target == worked.blockPos, "the Dratini did not bind to the first burner: $binding")
        helper.onEachTick {
            helper.assertTrue(
                TestKit.claimOf(helper, dragon)?.key != TestKit.keyOf(helper, unworked.blockPos),
                "the worker took the second burner",
            )
            val ticks = workedTicks(helper, dragon, worked) ?: return@onEachTick
            val left = worked.getRemainingBurnTime()
            val other = unworked.getRemainingBurnTime()
            helper.assertTrue(left == other, "CD-BRN-04: the burn times read $left and $other")
            if (ticks >= 400) {
                helper.assertTrue(left > 0, "the burners ran out of fuel before 400 ticks of work")
                helper.succeed()
            }
        }
    }

    /**
     * A creative Blaze Burner holds its heat whatever heats it, so a worker gains nothing there. A Lv10 Dratini claims it, but on its spot
     * it waits in Standby and never Works, and its Work Stamina never drops (carry-over from M3).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun creativeBlazeBurnerHasNoDemand(helper: GameTestHelper) {
        val scene = scene(helper)
        val burner = CreateKit.filledBurner(helper, burnerRel)
        val registries = helper.level.registryAccess()
        val saved = burner.saveWithFullMetadata(registries)
        saved.putBoolean("isCreative", true)
        burner.loadWithComponents(saved, registries)
        helper.assertTrue(burner.isCreative(), "the burner did not load as a creative burner")
        CreateKit.basin(helper, aboveRel)
        val dragon = TestKit.tether(helper, scene.pasture, scene.owner, lowDragon)
        var standbySince = -1L
        helper.onEachTick {
            val worker = TestKit.workerOf(helper, dragon) ?: return@onEachTick
            helper.assertTrue(worker.status != WorkerStatus.WORKING, "the Dratini works a creative burner")
            helper.assertTrue(worker.stamina.fraction == 1.0, "Work Stamina dropped at a creative burner: ${worker.stamina}")
            if (worker.status != WorkerStatus.STANDBY) return@onEachTick
            helper.assertTrue(
                TestKit.claimOf(helper, dragon)?.key == TestKit.keyOf(helper, burner.blockPos),
                "the Dratini waits elsewhere",
            )
            if (standbySince < 0) standbySince = helper.tick
            if (helper.tick - standbySince >= 100) helper.succeed()
        }
    }

    /** A filled burner at [rel] with 1,000 ticks of normal fuel, as Create saves one that burns coal. */
    private fun fueledBurner(helper: GameTestHelper, rel: BlockPos): BlazeBurnerBlockEntity {
        val burner = CreateKit.filledBurner(helper, rel)
        val registries = helper.level.registryAccess()
        val saved = burner.saveWithFullMetadata(registries)
        saved.putInt("fuelLevel", BlazeBurnerBlockEntity.FuelType.NORMAL.ordinal)
        saved.putInt("burnTimeRemaining", FUEL_TICKS)
        burner.loadWithComponents(saved, registries)
        val fuel = burner.getActiveFuel()
        helper.assertTrue(fuel == BlazeBurnerBlockEntity.FuelType.NORMAL, "the burner at $rel holds $fuel")
        val left = burner.getRemainingBurnTime()
        helper.assertTrue(left == FUEL_TICKS, "the burner at $rel holds $left ticks of fuel")
        return burner
    }

    private companion object {
        const val FUEL_TICKS = 1_000

        /** A Dragonair is 1.1 × 0.9 blocks wide at size 1, and Cobblemon rolls its size within about 5% of that. */
        const val WIDE_SCALE = 1.05f
        const val NARROW_SCALE = 0.96f

        /** How far a held worker may drift before the hold puts it back, and how long each tick of the hold pauses its wander. */
        const val HOLD_DRIFT = 0.02
        const val HOLD_PAUSE_TICKS = 2L

        /** The height of the plate at the foot of the pasture block's top half. */
        const val PLATE_TOP = 0.0625

        /** Within the travel timeout of 600 ticks, with room for a stuck check of 100 ticks and 2 path countdowns of up to 39 ticks. */
        const val PRESSED_LIMIT = 400L
    }

    private fun assertBoiler(helper: GameTestHelper, expected: HeatLevel, heat: Int) {
        val level = CreateKit.heatAt(helper, burnerRel)
        helper.assertTrue(level == expected, "the burner under the boiler reads $level, expected $expected")
        val heater = CreateKit.heaterHeat(helper, burnerRel)
        helper.assertTrue(heater == heat.toFloat(), "Create's boiler heater registry gives the burner $heater heat, expected $heat")
        val boiler = CreateKit.boilerHeat(helper, aboveRel)
        helper.assertTrue(boiler == heat, "the boiler counts $boiler heat from the burner, expected $heat")
    }
}
