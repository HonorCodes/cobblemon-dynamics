package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.ai.BrainBridge
import codes.honor.cobblemondynamics.config.EngineSettings
import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.engine.WorkArea
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.engine.ZoneTether
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.pasture.PastureAdapter
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.AfterBatch
import net.minecraft.gametest.framework.BeforeBatch
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.AbstractFurnaceBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.CropBlock
import net.minecraft.world.level.block.FarmBlock
import net.minecraft.world.level.block.LeavesBlock
import net.minecraft.world.level.block.SlabBlock
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity
import net.minecraft.world.level.block.state.properties.SlabType
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class TerrainSpotTests {
    private fun walledFurnace(helper: GameTestHelper, rel: BlockPos, openSide: Direction): AbstractFurnaceBlockEntity {
        val furnace = TestKit.furnace(helper, rel, ItemStack(Items.RAW_IRON, 32))
        helper.setBlock(rel.above(), Blocks.STONE)
        for (side in Direction.Plane.HORIZONTAL) {
            if (side == openSide) continue
            val wall = rel.relative(side)
            helper.setBlock(wall, Blocks.STONE)
            helper.setBlock(wall.above(), Blocks.STONE)
        }
        return furnace
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 600)
    fun cdNav01And02WorkerSnapsOnCarpetWithoutCollision(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 11), owner)
        TestKit.workMode(pasture, true)
        val furnaceRel = BlockPos(7, 2, 5)
        val frontRel = BlockPos(7, 2, 6)
        val furnace = walledFurnace(helper, furnaceRel, Direction.SOUTH)
        for (x in 6..8) {
            for (z in 6..9) {
                helper.setBlock(BlockPos(x, 2, z), Blocks.RED_CARPET)
            }
        }
        val charmander = TestKit.tether(helper, pasture, owner, "charmander level=20")
        TestKit.moveTo(helper, charmander, BlockPos(7, 2, 10))
        val strayGoal = helper.absolutePos(BlockPos(1, 2, 13))
        if (charmander.brain.checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)) {
            charmander.brain.setMemory(MemoryModuleType.WALK_TARGET, WalkTarget(strayGoal, 0.4f, 0))
        }
        if (charmander.brain.checkMemory(MemoryModuleType.ANGRY_AT, MemoryStatus.REGISTERED)) {
            charmander.brain.setMemory(MemoryModuleType.ANGRY_AT, UUID.randomUUID())
        }
        val expectedSpot = helper.absolutePos(frontRel)
        helper.runAtTickTime(599) {
            val w = TestKit.workerOf(helper, charmander)
            helper.fail(
                "cdNav01And02 timeout: status=${w?.status}, spot=${w?.spot} (expected=$expectedSpot), " +
                    "pos=${charmander.position()}, onGround=${charmander.onGround()}, " +
                    "skips=${w?.skipUntil}, claim=${TestKit.claimOf(helper, charmander)}, " +
                    "frontBlock=${helper.level.getBlockState(
                        expectedSpot,
                    )}, belowFront=${helper.level.getBlockState(expectedSpot.below())}",
            )
        }
        helper.onEachTick {
            val worker = TestKit.workerOf(helper, charmander) ?: return@onEachTick
            if (BrainBridge.isWorking(charmander) && charmander.brain.checkMemory(MemoryModuleType.ANGRY_AT, MemoryStatus.REGISTERED)) {
                helper.assertFalse(
                    charmander.brain.hasMemoryValue(MemoryModuleType.ANGRY_AT),
                    "CD-NAV-10: residual ANGRY_AT remained after entering work",
                )
            }
            if (worker.status != WorkerStatus.WORKING) return@onEachTick
            helper.assertTrue(worker.spot == expectedSpot, "CD-NAV-01: expected carpet spot $expectedSpot, got ${worker.spot}")
            val expectedY = expectedSpot.y + 0.0635
            helper.assertTrue(
                abs(charmander.y - expectedY) <= 0.01,
                "CD-NAV-02: expected snapped Y $expectedY +- 0.01 on carpet, got ${charmander.y}",
            )
            helper.assertTrue(
                helper.level.noBlockCollision(charmander, charmander.boundingBox.deflate(1e-4)),
                "CD-NAV-02: worker snapped onto carpet collides with blocks",
            )
            val lit = helper.level.getBlockState(furnace.blockPos).getValue(AbstractFurnaceBlock.LIT)
            if (lit) helper.succeed()
        }
    }

    @GameTest(template = "floor_23x10x23", timeoutTicks = 800)
    fun cdNav01WorkersStandOnFarmlandDirtPathBottomSlabAndLeaves(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(11, 1, 11), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)

        val farmFurnaceRel = BlockPos(6, 2, 6)
        val farmFurnace = walledFurnace(helper, farmFurnaceRel, Direction.SOUTH)
        val wetFarmland = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7)
        for (x in 5..7) for (z in 7..9) helper.setBlock(BlockPos(x, 1, z), wetFarmland)

        val pathFurnaceRel = BlockPos(16, 2, 6)
        val pathFurnace = walledFurnace(helper, pathFurnaceRel, Direction.SOUTH)
        for (x in 15..17) for (z in 7..9) helper.setBlock(BlockPos(x, 1, z), Blocks.DIRT_PATH)

        val slabFurnaceRel = BlockPos(6, 2, 16)
        val slabFurnace = walledFurnace(helper, slabFurnaceRel, Direction.NORTH)
        val bottomSlab = Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM)
        for (x in 5..7) for (z in 13..15) helper.setBlock(BlockPos(x, 2, z), bottomSlab)

        val leafFurnaceRel = BlockPos(16, 2, 16)
        val leafFurnace = walledFurnace(helper, leafFurnaceRel, Direction.NORTH)
        val leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true)
        for (x in 15..17) for (z in 13..15) helper.setBlock(BlockPos(x, 1, z), leaves)

        data class Case(
            val label: String,
            val worker: PokemonEntity,
            val furnace: AbstractFurnaceBlockEntity,
            val spotRel: BlockPos,
            val expectedOffsetY: Double,
        )

        val cases = listOf(
            Triple("farmland", farmFurnaceRel to farmFurnace, BlockPos(6, 2, 7) to -0.0615),
            Triple("dirt_path", pathFurnaceRel to pathFurnace, BlockPos(16, 2, 7) to -0.0615),
            Triple("bottom_slab", slabFurnaceRel to slabFurnace, BlockPos(6, 2, 15) to 0.501),
            Triple("leaves", leafFurnaceRel to leafFurnace, BlockPos(16, 2, 15) to 0.001),
        ).map { (label, pair, spotAndOffset) ->
            val entity = TestKit.tether(helper, pasture, owner, "charmander level=20")
            val bound = TestKit.bind(helper, owner, entity, pair.first)
            helper.assertTrue(bound != null, "CD-NAV-01: bind to $label furnace failed: ${TestKit.messages(owner)}")
            Case(label, entity, pair.second, spotAndOffset.first, spotAndOffset.second)
        }

        helper.onEachTick {
            val allWorking = cases.all { c ->
                val worker = TestKit.workerOf(helper, c.worker) ?: return@all false
                val lit = helper.level.getBlockState(c.furnace.blockPos).getValue(AbstractFurnaceBlock.LIT)
                worker.status == WorkerStatus.WORKING && lit
            }
            if (!allWorking) return@onEachTick
            for (c in cases) {
                val worker = TestKit.workerOf(helper, c.worker)!!
                val expectedSpot = helper.absolutePos(c.spotRel)
                helper.assertTrue(worker.spot == expectedSpot, "CD-NAV-01 (${c.label}): expected spot $expectedSpot, got ${worker.spot}")
                val expectedY = expectedSpot.y + c.expectedOffsetY
                helper.assertTrue(
                    abs(c.worker.y - expectedY) <= 0.02,
                    "CD-NAV-02 (${c.label}): expected Y $expectedY +- 0.02, got ${c.worker.y}",
                )
                helper.assertTrue(
                    helper.level.noBlockCollision(c.worker, c.worker.boundingBox.deflate(1e-4)),
                    "CD-NAV-02 (${c.label}): worker collides with blocks",
                )
            }
            helper.succeed()
        }
    }

    @GameTest(template = "floor_23x10x23", timeoutTicks = 600)
    fun cdNav03CarpetRingNeverBlocksChestMagnetOrFurnaceStoker(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(11, 1, 11), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)

        val chestRel = BlockPos(7, 2, 7)
        helper.setBlock(chestRel, Blocks.CHEST)
        for (dx in -2..2) {
            for (dz in -2..2) {
                if (dx == 0 && dz == 0) continue
                val ring = if (max(abs(dx), abs(dz)) == 1) Blocks.MOSS_CARPET else Blocks.RED_CARPET
                helper.setBlock(chestRel.offset(dx, 0, dz), ring)
            }
        }

        val furnaceRel = BlockPos(15, 2, 7)
        val furnace = TestKit.furnace(helper, furnaceRel, ItemStack(Items.RAW_IRON, 32))
        for (dx in -2..2) {
            for (dz in -2..2) {
                if (dx == 0 && dz == 0) continue
                val ring = if (max(abs(dx), abs(dz)) == 1) Blocks.MOSS_CARPET else Blocks.WHITE_CARPET
                helper.setBlock(furnaceRel.offset(dx, 0, dz), ring)
            }
        }

        val klink = TestKit.tether(helper, pasture, owner, "klink level=100")
        helper.assertTrue(
            TestKit.bind(helper, owner, klink, chestRel) != null,
            "CD-NAV-03: carpet-ringed chest refused Magnet bind: ${TestKit.messages(owner)}",
        )
        val dropAt = helper.absoluteVec(BlockPos(9, 2, 11).bottomCenter)
        val drop = ItemEntity(helper.level, dropAt.x, dropAt.y, dropAt.z, ItemStack(Items.COBBLESTONE, 4), 0.0, 0.0, 0.0)
        drop.setNoPickUpDelay()
        helper.level.addFreshEntity(drop)

        val charmander = TestKit.tether(helper, pasture, owner, "charmander level=20")
        var magnetEnteredWorking = false
        helper.runAtTickTime(599) {
            val kw = TestKit.workerOf(helper, klink)
            val cw = TestKit.workerOf(helper, charmander)
            helper.fail(
                "cdNav03 timeout: klink=${kw?.status} spot=${kw?.spot} pos=${klink.position()} skips=${kw?.skipUntil} " +
                    "dropAlive=${drop.isAlive} chestCount=${TestKit.countIn(helper, chestRel, Items.COBBLESTONE)}; " +
                    "charmander=${cw?.status} spot=${cw?.spot} pos=${charmander.position()} skips=${cw?.skipUntil} " +
                    "lit=${helper.level.getBlockState(furnace.blockPos).getValue(AbstractFurnaceBlock.LIT)}",
            )
        }
        helper.onEachTick {
            if (TestKit.workerOf(helper, klink)?.status == WorkerStatus.WORKING) magnetEnteredWorking = true
            val stokerWorking = TestKit.workerOf(helper, charmander)?.status == WorkerStatus.WORKING &&
                helper.level.getBlockState(furnace.blockPos).getValue(AbstractFurnaceBlock.LIT)
            val collected = !drop.isAlive && TestKit.countIn(helper, chestRel, Items.COBBLESTONE) == 4
            if (stokerWorking && collected) {
                val klinkStatus = TestKit.workerOf(helper, klink)?.status
                helper.assertTrue(
                    magnetEnteredWorking || klinkStatus == WorkerStatus.STANDBY,
                    "CD-NAV-03: Magnet collected items without Working/Standby ($klinkStatus)",
                )
                helper.succeed()
            }
        }
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_600)
    fun cdNav10HarvesterChainsThreeByThreeWheatWithoutWanderGap(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 11), owner)
        TestKit.workMode(pasture, true)

        val soil = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7)
        val ripeWheat = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7)
        val crops = ArrayList<BlockPos>(9)
        for (x in 6..8) {
            for (z in 4..6) {
                helper.setBlock(BlockPos(x, 1, z), soil)
                val cropRel = BlockPos(x, 2, z)
                helper.setBlock(cropRel, ripeWheat)
                crops += cropRel
            }
        }

        val bulbasaur = TestKit.tether(helper, pasture, owner, "bulbasaur level=20")
        TestKit.moveTo(helper, bulbasaur, BlockPos(7, 2, 7))
        val minCorner = helper.absolutePos(BlockPos(6, 2, 4))
        val maxCorner = helper.absolutePos(BlockPos(8, 2, 6))
        val minX = minCorner.x - 2.5
        val maxX = maxCorner.x + 3.5
        val minZ = minCorner.z - 2.5
        val maxZ = maxCorner.z + 3.5

        var started = false
        var idleStreak = 0
        helper.onEachTick {
            val worker = TestKit.workerOf(helper, bulbasaur) ?: return@onEachTick
            if (worker.status == WorkerStatus.WORKING) started = true
            if (!started) return@onEachTick
            val remaining = crops.count { helper.getBlockState(it).getOptionalValue(CropBlock.AGE).orElse(-1) == 7 }
            if (remaining == 0) {
                val allReplanted = crops.all { helper.getBlockState(it).getOptionalValue(CropBlock.AGE).orElse(-1) == 0 }
                helper.assertTrue(allReplanted, "CD-NAV-10: expected all 9 crops replanted at age 0")
                helper.succeed()
                return@onEachTick
            }
            helper.assertTrue(
                bulbasaur.x in minX..maxX && bulbasaur.z in minZ..maxZ,
                "CD-NAV-10: Harvester wandered outside the 3x3 plot to (${bulbasaur.x}, ${bulbasaur.z}) with $remaining crops left",
            )
            idleStreak = if (worker.status == WorkerStatus.IDLE) idleStreak + 1 else 0
            if (worker.status == WorkerStatus.IDLE &&
                bulbasaur.brain.checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)
            ) {
                helper.assertFalse(
                    bulbasaur.brain.hasMemoryValue(MemoryModuleType.WALK_TARGET),
                    "CD-NAV-10: Harvester picked an idle WALK_TARGET between ripe crops",
                )
            }
            helper.assertTrue(
                idleStreak <= 12,
                "CD-NAV-10: Harvester stayed IDLE for $idleStreak consecutive ticks with $remaining ripe crops remaining",
            )
        }
    }

    @GameTest(template = "floor_41x8x41", timeoutTicks = 200)
    fun cdNav06EntityIdResolvesWorkerOutsidePastureRoamBox(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(2, 1, 2), owner)
        val charmander = TestKit.tether(helper, pasture, owner, "charmander level=20")
        TestKit.moveTo(helper, charmander, BlockPos(38, 2, 27))

        val dist = charmander.position().distanceTo(Vec3.atCenterOf(pasture.blockPos))
        helper.assertTrue(dist >= 40.0, "CD-NAV-06: expected worker >= 40 blocks from pasture, got $dist")
        val tethering = charmander.tethering
        helper.assertTrue(tethering != null, "CD-NAV-06: expected live tethering on pastured worker")
        helper.assertFalse(
            tethering!!.canRoamTo(charmander.blockPosition()),
            "CD-NAV-06: expected worker at ${charmander.blockPosition()} to be outside pasture roam box",
        )

        val resolvedList = PastureAdapter.tethered(pasture)
        helper.assertTrue(
            resolvedList.singleOrNull()?.entity === charmander,
            "CD-NAV-06: PastureAdapter.tethered failed to resolve entity via entityId outside roam box",
        )
        helper.assertTrue(
            PastureAdapter.tetheredEntity(helper.level, pasture.blockPos, charmander.pokemon.uuid) === charmander,
            "CD-NAV-06: PastureAdapter.tetheredEntity failed to resolve entity via entityId outside roam box",
        )

        TestKit.workMode(pasture, true)
        helper.onEachTick {
            if (TestKit.workerOf(helper, charmander) != null) helper.succeed()
        }
    }

    @GameTest(template = "floor_41x8x41", timeoutTicks = 1_600, batch = WAYPOINT_BATCH)
    fun cdNav07WorkerBeyondFollowRangeStepsWaypointsIntoWorking(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(20, 1, 20), owner)
        TestKit.workMode(pasture, true)

        val furnaceRel = BlockPos(3, 2, 3)
        val furnace = TestKit.furnace(helper, furnaceRel, ItemStack(Items.RAW_IRON, 64))
        val charmander = TestKit.tether(helper, pasture, owner, "charmander level=20")
        val startRel = BlockPos(35, 2, 35)
        TestKit.moveTo(helper, charmander, startRel)

        var claimed = false
        var sawIntermediateWaypoint = false
        helper.onEachTick {
            if (!claimed) {
                if (TestKit.claimOf(helper, charmander) == null) {
                    TestKit.moveTo(helper, charmander, startRel)
                    return@onEachTick
                }
                val spot = TestKit.workerOf(helper, charmander)?.spot
                helper.assertTrue(spot != null, "CD-NAV-07: claimed without a spot")
                val initialDist = charmander.position().distanceTo(Vec3.atBottomCenterOf(spot!!))
                helper.assertTrue(initialDist >= 42.0, "CD-NAV-07: expected start distance >= 42 blocks, got $initialDist")
                claimed = true
            }
            val worker = TestKit.workerOf(helper, charmander) ?: return@onEachTick
            val spot = worker.spot ?: return@onEachTick
            val goal = Vec3.atBottomCenterOf(spot)
            if (charmander.position().distanceTo(goal) > 32.0 &&
                charmander.brain.checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)
            ) {
                charmander.brain.getMemory(MemoryModuleType.WALK_TARGET).ifPresent { walkTarget ->
                    val wp = walkTarget.target.currentBlockPosition()
                    if (wp != spot) {
                        val stepDist = charmander.position().distanceTo(Vec3.atBottomCenterOf(wp))
                        helper.assertTrue(
                            stepDist <= 32.0,
                            "CD-NAV-07: intermediate waypoint $wp exceeds 32-block follow range ($stepDist)",
                        )
                        sawIntermediateWaypoint = true
                    }
                }
            }
            val lit = helper.level.getBlockState(furnace.blockPos).getValue(AbstractFurnaceBlock.LIT)
            if (worker.status == WorkerStatus.WORKING && lit) {
                helper.assertTrue(sawIntermediateWaypoint, "CD-NAV-07: worker never used an intermediate waypoint while > 32 blocks away")
                helper.succeed()
            }
        }
    }

    @GameTest(template = "floor_41x8x41", timeoutTicks = 1_200)
    fun cdNav05ZoneTetherPullsIdleWorkerInsideAreaAndSuppressesOwnerFollow(helper: GameTestHelper) {
        val owner = TestKit.listedOwner(helper, BlockPos(35, 2, 29))
        val onPasture = TestKit.placePasture(helper, BlockPos(9, 1, 9), owner)
        TestKit.workMode(onPasture, true)

        val offPasture = TestKit.placePasture(helper, BlockPos(9, 1, 35), owner)

        val idleWorker = TestKit.tether(helper, onPasture, owner, "charmander level=20")
        TestKit.moveTo(helper, idleWorker, BlockPos(35, 2, 9))

        val offWorker = TestKit.tether(helper, offPasture, owner, "charmander level=20")
        TestKit.moveTo(helper, offWorker, BlockPos(25, 2, 35))

        val area = WorkArea.of(onPasture.blockPos, WorkEngineSettings.current, PastureAdapter.maxWanderDistance())
        helper.assertFalse(
            ZoneTether.isInsideMargin(idleWorker.position(), area),
            "CD-NAV-05: worker at (35, 2, 9) must start 18 blocks outside the 8-radius workArea",
        )

        var returnedAt = -1L
        val pullOwnerPos = helper.absoluteVec(BlockPos(26, 2, 9).bottomCenter)
        helper.onEachTick {
            helper.assertFalse(
                BrainBridge.isWorking(offWorker) || BrainBridge.holdsOrder(offWorker),
                "CD-NAV-05: Work Mode off pasture worker was modified by ZoneTether",
            )
            if (returnedAt < 0) {
                val distToOwner = idleWorker.position().distanceTo(owner.position())
                helper.assertTrue(distToOwner >= 15.0, "CD-NAV-05: idle worker detoured toward its owner ($distToOwner blocks away)")
                if (ZoneTether.isInsideArea(idleWorker.position(), area)) {
                    returnedAt = helper.tick
                    TestKit.place(owner, pullOwnerPos.x, pullOwnerPos.y, pullOwnerPos.z)
                }
                return@onEachTick
            }
            helper.assertTrue(
                ZoneTether.isInsideMargin(idleWorker.position(), area),
                "CD-NAV-05: idle worker drifted outside workArea + 2 margin after returning (${idleWorker.position()})",
            )
            if (helper.tick - returnedAt >= 60) {
                helper.assertFalse(
                    BrainBridge.isWorking(idleWorker),
                    "CD-NAV-05: idle worker inside workArea did not return to Activity.IDLE within 60 ticks",
                )
                helper.succeed()
            }
        }
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_600, batch = SLOW_SCAN_BATCH)
    fun cdNav10DarkGrassWorkerHarvestsNearerWheatOverPastureSentry(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 12), owner)
        TestKit.workMode(pasture, true)

        val soil = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7)
        val ripeWheat = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7)
        val crops = listOf(BlockPos(7, 2, 4), BlockPos(8, 2, 4))
        for (cropRel in crops) {
            helper.setBlock(cropRel.below(), soil)
            helper.setBlock(cropRel, ripeWheat)
        }

        val nuzleaf = TestKit.tether(helper, pasture, owner, "nuzleaf level=20")
        TestKit.moveTo(helper, nuzleaf, BlockPos(7, 2, 5))
        var started = false

        helper.onEachTick {
            val worker = TestKit.workerOf(helper, nuzleaf) ?: return@onEachTick
            if (worker.status == WorkerStatus.WORKING) started = true
            val remaining = crops.count { helper.getBlockState(it).getOptionalValue(CropBlock.AGE).orElse(-1) == 7 }
            if (remaining > 0) {
                val activeRole = TestKit.engine(helper).roleOf(nuzleaf.pokemon.uuid)?.definition?.id
                helper.assertFalse(
                    activeRole == id("sentry"),
                    "CD-NAV-10: Dark/Grass worker got trapped on farther Sentry while $remaining nearer ripe wheat crops remained",
                )
                if (started &&
                    worker.status == WorkerStatus.IDLE &&
                    nuzleaf.brain.checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)
                ) {
                    helper.assertFalse(
                        nuzleaf.brain.hasMemoryValue(MemoryModuleType.WALK_TARGET),
                        "CD-NAV-10: Dark/Grass worker started an idle walk during 64-blocks/tick chained scan",
                    )
                }
                return@onEachTick
            }
            val allReplanted = crops.all { helper.getBlockState(it).getOptionalValue(CropBlock.AGE).orElse(-1) == 0 }
            helper.assertTrue(allReplanted, "CD-NAV-10: expected nearer ripe wheat crops harvested and replanted")
            helper.succeed()
        }
    }

    @GameTest(template = "floor_41x8x41", timeoutTicks = 2_400, batch = WAYPOINT_BATCH)
    fun cdNav07WorkerBeyondFollowRangeDetoursAroundMidpointWall(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(20, 1, 20), owner)
        TestKit.workMode(pasture, true)

        for (x in 12..40) {
            for (y in 2..5) {
                helper.setBlock(BlockPos(x, y, 22), Blocks.STONE)
            }
        }

        val furnaceRel = BlockPos(3, 2, 3)
        val furnace = TestKit.furnace(helper, furnaceRel, ItemStack(Items.RAW_IRON, 64))
        val charmander = TestKit.tether(helper, pasture, owner, "charmander level=20")
        val startRel = BlockPos(35, 2, 35)
        TestKit.moveTo(helper, charmander, startRel)

        var claimed = false
        var sawReachableDetour = false
        helper.onEachTick {
            if (!claimed) {
                if (TestKit.claimOf(helper, charmander) == null) {
                    TestKit.moveTo(helper, charmander, startRel)
                    return@onEachTick
                }
                val spot = TestKit.workerOf(helper, charmander)?.spot
                helper.assertTrue(spot != null, "CD-NAV-07: claimed without a spot")
                val initialDist = charmander.position().distanceTo(Vec3.atBottomCenterOf(spot!!))
                helper.assertTrue(initialDist >= 42.0, "CD-NAV-07: expected start distance >= 42 blocks, got $initialDist")
                claimed = true
            }
            val worker = TestKit.workerOf(helper, charmander) ?: return@onEachTick
            val spot = worker.spot ?: return@onEachTick
            if (charmander.position().distanceTo(Vec3.atBottomCenterOf(spot)) > 32.0 &&
                charmander.brain.checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)
            ) {
                charmander.brain.getMemory(MemoryModuleType.WALK_TARGET).ifPresent { walkTarget ->
                    val wp = walkTarget.target.currentBlockPosition()
                    if (wp != spot) {
                        val relWp = TestKit.relative(helper, wp)
                        helper.assertFalse(
                            relWp.x >= 12 && relWp.z <= 22,
                            "CD-NAV-07: picked unreachable waypoint $relWp in or behind the midpoint wall",
                        )
                        if (relWp.z - relWp.x >= 6) sawReachableDetour = true
                    }
                }
            }
            val lit = helper.level.getBlockState(furnace.blockPos).getValue(AbstractFurnaceBlock.LIT)
            if (worker.status == WorkerStatus.WORKING && lit) {
                helper.assertTrue(sawReachableDetour, "CD-NAV-07: worker did not pick a detour waypoint past the midpoint wall")
                helper.succeed()
            }
        }
    }

    private var savedWaypointSettings: EngineSettings? = null
    private var savedSlowScanSettings: EngineSettings? = null

    @BeforeBatch(batch = WAYPOINT_BATCH)
    fun configureWaypointBatch(@Suppress("UNUSED_PARAMETER") level: ServerLevel) {
        val current = WorkEngineSettings.current
        savedWaypointSettings = current
        WorkEngineSettings.current = current.copy(horizontalRadius = 20, maxHorizontalRadius = 20)
    }

    @AfterBatch(batch = WAYPOINT_BATCH)
    fun restoreWaypointBatch(@Suppress("UNUSED_PARAMETER") level: ServerLevel) {
        WorkEngineSettings.current = savedWaypointSettings ?: EngineSettings.DEFAULT.copy(baseSlackChance = 0.0)
        savedWaypointSettings = null
    }

    @BeforeBatch(batch = SLOW_SCAN_BATCH)
    fun configureSlowScanBatch(@Suppress("UNUSED_PARAMETER") level: ServerLevel) {
        val current = WorkEngineSettings.current
        savedSlowScanSettings = current
        WorkEngineSettings.current = current.copy(maxBlockChecksPerTick = 64)
    }

    @AfterBatch(batch = SLOW_SCAN_BATCH)
    fun restoreSlowScanBatch(@Suppress("UNUSED_PARAMETER") level: ServerLevel) {
        WorkEngineSettings.current = savedSlowScanSettings ?: EngineSettings.DEFAULT.copy(baseSlackChance = 0.0)
        savedSlowScanSettings = null
    }

    companion object {
        const val WAYPOINT_BATCH = "nav_waypoints"
        const val SLOW_SCAN_BATCH = "nav_sentry_vs_harvest"
    }
}
