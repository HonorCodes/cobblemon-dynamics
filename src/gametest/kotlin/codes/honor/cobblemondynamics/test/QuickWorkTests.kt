package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.ai.BrainBridge
import codes.honor.cobblemondynamics.claim.TargetKey
import codes.honor.cobblemondynamics.engine.WorkEngine
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.stamina.Stamina
import codes.honor.cobblemondynamics.stamina.StaminaStore
import codes.honor.cobblemondynamics.test.create.CreateKit
import com.cobblemon.mod.common.pokemon.Pokemon
import com.mojang.authlib.GameProfile
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.AbstractFurnaceBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.common.util.FakePlayer
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import java.util.UUID

/**
 * Quick Work (ADR-0013): a party Pokémon that its owner sends out at a furnace works it. The tests list fake players in the player
 * list, so they run in their own batch. Each test has one owner.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class QuickWorkTests {
    private class Scene(
        val owner: ServerPlayer,
        val charmander: Pokemon,
        val a: AbstractFurnaceBlockEntity,
        val b: AbstractFurnaceBlockEntity,
    )

    /** Furnaces A and B with 8 raw iron each, and a listed owner 10 blocks east of A who looks at A, with a Charmander in slot 0. */
    private fun scene(helper: GameTestHelper): Scene {
        val a = TestKit.furnace(helper, A, ItemStack(Items.RAW_IRON, 8))
        val b = TestKit.furnace(helper, B, ItemStack(Items.RAW_IRON, 8))
        val owner = TestKit.listedOwner(helper, OWNER)
        val charmander = TestKit.partyPokemon(owner, "charmander level=10")
        TestKit.lookAt(helper, owner, A)
        return Scene(owner, charmander, a, b)
    }

    private fun claimedBy(helper: GameTestHelper, pokemon: Pokemon): BlockPos? =
        (TestKit.engine(helper).claims.claimOf(pokemon.uuid)?.key as? TargetKey.Block)?.let { BlockPos.of(it.pos) }

    @GameTest(template = "floor_23x10x23", timeoutTicks = 1_000, batch = BATCH)
    fun cdQwk01Qwk02Qwk11Qwk12SendOutAtAFurnaceWorksIt(helper: GameTestHelper) {
        val s = scene(helper)
        TestKit.pressSendOut(s.owner)
        // CD-QWK-01: the aim at the key press counts, so a turn to furnace B before the send-out ends changes nothing.
        helper.runAfterDelay(1) { TestKit.lookAt(helper, s.owner, B) }
        var workingSince = -1L
        helper.onEachTick {
            val engine = TestKit.engine(helper)
            if (helper.tick == 100L && claimedBy(helper, s.charmander) == null) helper.fail("CD-QWK-02: no claim after 100 ticks")
            if (helper.tick == 400L && workingSince < 0) helper.fail("CD-QWK-02: not Working after 400 ticks")
            val worker = engine.worker(s.charmander.uuid) ?: return@onEachTick
            val entity = engine.entityOf(s.charmander.uuid) ?: return@onEachTick
            if (worker.status != WorkerStatus.WORKING) return@onEachTick
            if (workingSince < 0) workingSince = helper.tick
            // CD-QWK-12: Cobblemon's follow-owner task would walk it to its owner, who stands more than 5 blocks away.
            val spot = worker.spot ?: throw GameTestAssertException("a Working worker with no spot")
            if (!WorkEngine.isArrived(entity, spot)) helper.fail("CD-QWK-12: the Charmander left its work spot at tick ${helper.tick}")
        }
        helper.succeedWhen {
            val engine = TestKit.engine(helper)
            helper.assertTrue(claimedBy(helper, s.charmander) == s.a.blockPos, "CD-QWK-01: the order is not on furnace A")
            helper.assertTrue(engine.claims.holderOf(TestKit.keyOf(helper, s.b.blockPos)) == null, "CD-QWK-01: furnace B is claimed")
            helper.assertTrue(
                TestKit.messages(s.owner).contains("Charmander goes to work on Furnace"),
                "CD-QWK-11: ${TestKit.messages(s.owner)}",
            )
            helper.assertTrue(workingSince >= 0 && helper.tick - workingSince >= 200, "CD-QWK-12: fewer than 200 ticks of Working")
            val lit = helper.level.getBlockState(s.a.blockPos).getValue(AbstractFurnaceBlock.LIT)
            helper.assertTrue(lit, "CD-QWK-02: furnace A is not lit")
            helper.assertTrue((engine.worker(s.charmander.uuid)?.stamina?.fraction ?: 1.0) < 1.0, "CD-QWK-02: no Work Stamina drained")
        }
    }

    /**
     * One owner sends out an Alakazam at a chest and a Squirtle at a cauldron in the same tick. Only leash-only roles fit those blocks,
     * Telekinesis and the Pump, so neither gets an order, and the owner learns that each block needs the Work Leash (CD-QWK-04).
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 300, batch = BATCH)
    fun cdQwk04ChestAndCauldronNeedTheLeash(helper: GameTestHelper) {
        helper.setBlock(A, Blocks.CHEST)
        helper.setBlock(B, Blocks.CAULDRON)
        val owner = TestKit.listedOwner(helper, OWNER)
        val alakazam = TestKit.partyPokemon(owner, "alakazam level=40")
        val squirtle = TestKit.partyPokemon(owner, "squirtle level=10")
        sendOutAt(helper, owner, alakazam, A, slot = 0)
        sendOutAt(helper, owner, squirtle, B, slot = 1)
        assertNoOrder(helper, "CD-QWK-04", alakazam, squirtle)
        helper.succeedWhen {
            val messages = TestKit.messages(owner)
            val both = messages.containsAll(listOf("Chest needs the Work Leash", "Cauldron needs the Work Leash"))
            helper.assertTrue(both, "CD-QWK-04: the owner got $messages")
        }
    }

    /**
     * A Water and Fairy Azumarill sent out at a dry Create water wheel stays out for 200 ticks after its send-out with no order, no
     * claim of any kind and no message, because the engine treats a Fairy-type as a healer (CD-QWK-05).
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 400, batch = BATCH)
    fun cdQwk05FairyTypeGetsNoOrder(helper: GameTestHelper) {
        CreateKit.smallWheel(helper, A)
        val owner = TestKit.listedOwner(helper, OWNER)
        val azumarill = TestKit.partyPokemon(owner, "azumarill level=30")
        sendOutAt(helper, owner, azumarill, A)
        assertNoOrder(helper, "CD-QWK-05", azumarill)
        helper.onEachTick { helper.assertTrue(TestKit.messages(owner).isEmpty(), "CD-QWK-05: the owner got ${TestKit.messages(owner)}") }
        helper.succeedWhen {
            helper.assertTrue(helper.tick >= SEND_OUT_TICKS + 200, "the Azumarill has not been out for 200 ticks")
            helper.assertTrue(azumarill.entity != null, "the Azumarill is no longer out")
        }
    }

    /**
     * One owner, whom `mayInteract` refuses the furnace, sends out a Charmander at furnace A. Then the same owner, no longer refused but in
     * spectator mode, sends out a second Charmander at furnace B. Neither gets an order, and each send-out tells the owner that it may not
     * use the furnace (CD-QWK-06).
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 400, batch = BATCH)
    fun cdQwk06RefusedOwnerAndSpectatorGetNoOrder(helper: GameTestHelper) {
        TestKit.furnace(helper, A, ItemStack(Items.RAW_IRON, 8))
        TestKit.furnace(helper, B, ItemStack(Items.RAW_IRON, 8))
        val owner = TestKit.listed(helper, RefusedOwner(helper.level), OWNER)
        val first = TestKit.partyPokemon(owner, "charmander level=10")
        val second = TestKit.partyPokemon(owner, "charmander level=10")
        assertNoOrder(helper, "CD-QWK-06", first, second)
        fun refusals() = TestKit.messages(owner).count { it == "You may not use Furnace" }
        helper.startSequence()
            .thenExecute { sendOutAt(helper, owner, first, A, slot = 0) }
            .thenWaitUntil { helper.assertTrue(refusals() == 1, "CD-QWK-06: the refused owner got ${TestKit.messages(owner)}") }
            .thenExecute {
                owner.refuses = false
                helper.assertTrue(owner.setGameMode(GameType.SPECTATOR) && owner.isSpectator, "the owner is not in spectator mode")
                sendOutAt(helper, owner, second, B, slot = 1)
            }
            .thenWaitUntil { helper.assertTrue(refusals() == 2, "CD-QWK-06: the spectator got ${TestKit.messages(owner)}") }
            .thenSucceed()
    }

    /**
     * A Charmander of the owner's own Work Mode pasture works furnace A. The owner then sends out a party Charmander at furnace A: it gets
     * no order and no claim and stays out, the pastured Charmander keeps the furnace, and the owner learns that the furnace is already
     * being worked (CD-QWK-07). Furnace B stays empty, so the pastured Charmander takes furnace A.
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 800, batch = BATCH)
    fun cdQwk07ClaimedFurnaceGivesNoOrder(helper: GameTestHelper) {
        val s = scene(helper)
        s.b.clearContent()
        val pasture = TestKit.placePasture(helper, PASTURE, s.owner)
        TestKit.workMode(pasture, true)
        val pastured = TestKit.tether(helper, pasture, s.owner, "charmander level=10")
        assertNoOrder(helper, "CD-QWK-07", s.charmander)
        var pressedAt = -1L
        helper.onEachTick {
            val working = TestKit.workerOf(helper, pastured)?.status == WorkerStatus.WORKING
            if (pressedAt < 0 && working && claimedBy(helper, pastured.pokemon) == s.a.blockPos) {
                sendOutAt(helper, s.owner, s.charmander, A)
                pressedAt = helper.tick
            }
        }
        helper.succeedWhen {
            helper.assertTrue(pressedAt >= 0, "the pastured Charmander does not work furnace A yet")
            val messages = TestKit.messages(s.owner)
            helper.assertTrue(messages.contains("Furnace is already being worked"), "CD-QWK-07: the owner got $messages")
            helper.assertTrue(claimedBy(helper, pastured.pokemon) == s.a.blockPos, "CD-QWK-07: the pastured Charmander lost furnace A")
            helper.assertTrue(s.charmander.entity != null, "CD-QWK-07: the send-out did not stay a normal one")
        }
    }

    /** A Charmander whose saved Work Stamina is 0% and Tired gets no order at a furnace with raw iron (CD-QWK-08). */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 300, batch = BATCH)
    fun cdQwk08TiredPokemonGetsNoOrder(helper: GameTestHelper) {
        val s = scene(helper)
        StaminaStore.save(s.charmander, Stamina(0.0, tired = true), helper.level.gameTime)
        sendOutAt(helper, s.owner, s.charmander, A)
        assertNoOrder(helper, "CD-QWK-08", s.charmander)
        helper.succeedWhen {
            val messages = TestKit.messages(s.owner)
            helper.assertTrue(messages.contains("Charmander is too Tired to work"), "CD-QWK-08: the owner got $messages")
        }
    }

    /** A Charmander sent out at an empty furnace gets no order, and the owner learns that the furnace needs no work now (CD-QWK-09). */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 300, batch = BATCH)
    fun cdQwk09EmptyFurnaceGivesNoOrder(helper: GameTestHelper) {
        val s = scene(helper)
        s.a.clearContent()
        sendOutAt(helper, s.owner, s.charmander, A)
        assertNoOrder(helper, "CD-QWK-09", s.charmander)
        helper.succeedWhen {
            val messages = TestKit.messages(s.owner)
            helper.assertTrue(messages.contains("Furnace needs no work now"), "CD-QWK-09: the owner got $messages")
        }
    }

    /**
     * A furnace with raw iron has stone on its four sides and a 5 × 5 stone roof one block above it, with a hole over the furnace. Its
     * owner stands on the roof and sends out a Charmander through the hole, onto the furnace. No cell in range sees the furnace past the
     * stone, so no work spot passes the checks of CD-ASG-09: the Charmander gets no order, and the owner learns that it cannot reach the
     * furnace (CD-QWK-10).
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 300, batch = BATCH)
    fun cdQwk10FurnaceWithNoSpotGivesNoOrder(helper: GameTestHelper) {
        TestKit.furnace(helper, PIT, ItemStack(Items.RAW_IRON, 8))
        for (side in listOf(PIT.east(), PIT.west(), PIT.north(), PIT.south())) helper.setBlock(side, Blocks.STONE)
        for (dx in -2..2) for (dz in -2..2) if (dx != 0 || dz != 0) helper.setBlock(PIT.offset(dx, 1, dz), Blocks.STONE)
        val owner = TestKit.listedOwner(helper, PIT.offset(1, 2, 0))
        // On the roof block east of the hole, near its west edge, so that the view to the furnace's center passes through the hole.
        val roof = helper.absoluteVec(Vec3(PIT.x + 1.1, PIT.y + 2.0, PIT.z + 0.5))
        TestKit.place(owner, roof.x, roof.y, roof.z)
        val charmander = TestKit.partyPokemon(owner, "charmander level=10")
        sendOutAt(helper, owner, charmander, PIT)
        assertNoOrder(helper, "CD-QWK-10", charmander)
        helper.succeedWhen {
            val messages = TestKit.messages(owner)
            helper.assertTrue(messages.contains("Charmander cannot reach Furnace"), "CD-QWK-10: the owner got $messages")
        }
    }

    @GameTest(template = "floor_23x10x23", timeoutTicks = 1_400, batch = BATCH)
    fun cdQwk13Qwk14Qwk15LeashEndsTheOrderForGood(helper: GameTestHelper) {
        val s = scene(helper)
        TestKit.pressSendOut(s.owner)
        var movedAt = -1L
        var endedAt = -1L
        var distanceAtEnd = 0.0
        helper.onEachTick {
            val engine = TestKit.engine(helper)
            val entity = s.charmander.entity ?: return@onEachTick
            val tick = helper.tick
            when {
                movedAt < 0 -> if (engine.worker(s.charmander.uuid)?.status == WorkerStatus.WORKING) {
                    // CD-QWK-13: 17.5 blocks from the box of furnace A, past the default leash of 16.
                    val far = helper.absoluteVec(FAR.bottomCenter)
                    TestKit.place(s.owner, far.x, far.y, far.z)
                    movedAt = tick
                }

                endedAt < 0 -> if (!engine.isQuickWorker(s.charmander.uuid) && engine.claims.claimOf(s.charmander.uuid) == null) {
                    endedAt = tick
                    distanceAtEnd = entity.distanceTo(s.owner).toDouble()
                } else if (tick - movedAt > 2) {
                    helper.fail("CD-QWK-13: the order outlived the leash by ${tick - movedAt} ticks")
                }

                tick == endedAt + 100 -> {
                    helper.assertTrue(!BrainBridge.holdsOrder(entity), "CD-QWK-15: the Brain still holds a work order")
                    val closer = entity.distanceTo(s.owner) <= distanceAtEnd - 2
                    helper.assertTrue(closer, "CD-QWK-15: the Charmander does not follow its owner")
                    val near = helper.absoluteVec(NEAR.bottomCenter)
                    TestKit.place(s.owner, near.x, near.y, near.z)
                    TestKit.lookAt(helper, s.owner, A)
                }

                tick > endedAt + 100 -> {
                    // CD-QWK-14: the owner is back beside furnace A and looks at it, and nothing starts again.
                    val holder = engine.claims.holderOf(TestKit.keyOf(helper, s.a.blockPos))
                    helper.assertTrue(holder == null, "CD-QWK-14: furnace A is claimed again")
                    helper.assertTrue(engine.worker(s.charmander.uuid) == null, "CD-QWK-14: the engine still holds a slot")
                    helper.assertTrue(engine.aimOf(s.charmander.uuid) == null, "CD-QWK-14: the engine still holds an aim")
                }
            }
        }
        helper.succeedWhen {
            helper.assertTrue(endedAt >= 0 && helper.tick >= endedAt + 300, "the test runs 200 ticks past the owner's return")
            helper.assertTrue(
                TestKit.messages(s.owner).contains("Charmander stopped working: you are too far from Furnace"),
                "CD-QWK-13: ${TestKit.messages(s.owner)}",
            )
        }
    }

    @GameTest(template = "floor_23x10x23", timeoutTicks = 1_000, batch = BATCH)
    fun cdQwk16RecallEndsTheOrder(helper: GameTestHelper) {
        val s = scene(helper)
        TestKit.pressSendOut(s.owner)
        var recalledAt = -1L
        helper.onEachTick {
            val engine = TestKit.engine(helper)
            if (recalledAt < 0) {
                if (engine.worker(s.charmander.uuid)?.status == WorkerStatus.WORKING) {
                    TestKit.pressSendOut(s.owner)
                    recalledAt = helper.tick
                }
            } else if (helper.tick - recalledAt > 2 && engine.claims.claimOf(s.charmander.uuid) != null) {
                helper.fail("CD-QWK-16: the order outlived the recall by ${helper.tick - recalledAt} ticks")
            }
        }
        helper.succeedWhen {
            helper.assertTrue(recalledAt >= 0 && helper.tick - recalledAt >= 40, "no recall yet")
            helper.assertTrue(s.charmander.entity == null, "Cobblemon did not recall the Charmander")
            helper.assertTrue(!TestKit.engine(helper).isQuickWorker(s.charmander.uuid), "CD-QWK-16: the order remains")
            helper.assertTrue(
                TestKit.messages(s.owner).contains("Charmander stopped working: it was recalled"),
                "CD-QWK-16: ${TestKit.messages(s.owner)}",
            )
        }
    }

    /**
     * A level 10 Charmander whose saved Work Stamina is 0.1% works furnace A until it is Tired. The order ends in the tick that makes it
     * Tired, so no tick shows the order with a Tired worker, its saved Work Stamina is Tired, and the owner learns why (CD-QWK-17).
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 600, batch = BATCH)
    fun cdQwk17TiredWorkerStops(helper: GameTestHelper) {
        val s = scene(helper)
        StaminaStore.save(s.charmander, Stamina(0.001), helper.level.gameTime)
        sendOutAt(helper, s.owner, s.charmander, A)
        var started = false
        var tiredAt = -1L
        helper.onEachTick {
            val engine = TestKit.engine(helper)
            if (engine.isQuickWorker(s.charmander.uuid)) started = true
            if (engine.worker(s.charmander.uuid)?.tired != true) return@onEachTick
            if (tiredAt < 0) tiredAt = helper.tick
            if (helper.tick > tiredAt) helper.fail("CD-QWK-17: the order still runs ${helper.tick - tiredAt} ticks after the Tired tick")
        }
        helper.succeedWhen {
            helper.assertTrue(started && !TestKit.engine(helper).isQuickWorker(s.charmander.uuid), "the order has not run and ended yet")
            val saved = StaminaStore.read(s.charmander.persistentData)
            helper.assertTrue(saved?.tired == true, "CD-QWK-17: the order ended with the Charmander at $saved")
            val messages = TestKit.messages(s.owner)
            helper.assertTrue(messages.contains("Charmander is Tired and stopped working"), "CD-QWK-17: the owner got $messages")
        }
    }

    /** Furnace A breaks while the Charmander works it. 2 ticks later the order is gone, and the owner learns why (CD-QWK-18). */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 600, batch = BATCH)
    fun cdQwk18BrokenFurnaceEndsTheOrder(helper: GameTestHelper) {
        val s = scene(helper)
        sendOutAt(helper, s.owner, s.charmander, A)
        var brokenAt = -1L
        helper.onEachTick {
            val engine = TestKit.engine(helper)
            if (brokenAt < 0) {
                if (engine.worker(s.charmander.uuid)?.status != WorkerStatus.WORKING) return@onEachTick
                helper.destroyBlock(A)
                brokenAt = helper.tick
            } else if (helper.tick - brokenAt >= 2 && engine.isQuickWorker(s.charmander.uuid)) {
                helper.fail("CD-QWK-18: the order outlived its furnace by ${helper.tick - brokenAt} ticks")
            }
        }
        helper.succeedWhen {
            helper.assertTrue(brokenAt >= 0 && helper.tick - brokenAt >= 2, "furnace A has not broken yet")
            val messages = TestKit.messages(s.owner)
            val reason = "Charmander stopped working: Furnace can no longer be worked"
            helper.assertTrue(messages.contains(reason), "CD-QWK-18: the owner got $messages")
        }
    }

    /**
     * Furnace A holds 1 raw iron. The Charmander smelts it and then waits in Standby with no work. The order ends after 200 ticks of
     * Standby, the default `engine.standbyMoveTicks`, and not before, and the owner learns why (CD-QWK-19).
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 900, batch = BATCH)
    fun cdQwk19StandbyTimeEndsTheOrder(helper: GameTestHelper) {
        val s = scene(helper)
        s.a.setItem(0, ItemStack(Items.RAW_IRON, 1))
        sendOutAt(helper, s.owner, s.charmander, A)
        var worked = false
        var standbyAt = -1L
        var endedAt = -1L
        helper.onEachTick {
            val engine = TestKit.engine(helper)
            val status = engine.worker(s.charmander.uuid)?.status
            when {
                endedAt >= 0 -> {}

                standbyAt >= 0 -> if (!engine.isQuickWorker(s.charmander.uuid)) endedAt = helper.tick

                status == WorkerStatus.WORKING -> worked = true

                status == WorkerStatus.STANDBY && worked -> {
                    standbyAt = helper.tick
                    val output = s.a.getItem(2)
                    helper.assertTrue(output.`is`(Items.IRON_INGOT), "the Charmander waits before the iron smelted: output $output")
                }
            }
        }
        helper.succeedWhen {
            helper.assertTrue(endedAt >= 0, "the order has not ended yet")
            val waited = endedAt - standbyAt
            helper.assertTrue(waited in 200L..201L, "CD-QWK-19: the order ended after $waited ticks of Standby")
            val messages = TestKit.messages(s.owner)
            val reason = "Charmander stopped working: Furnace needs no more work"
            helper.assertTrue(messages.contains(reason), "CD-QWK-19: the owner got $messages")
        }
    }

    /**
     * The owner leaves the server's player list while the Charmander works furnace A, as a logout takes a player out of it. 2 ticks later
     * the order and its claim are gone (CD-QWK-20). Cobblemon removes a party Pokémon's entity whose owner it cannot find, so the order
     * ends as for a recall here. The unit test of the end rule checks the reason of an owner who left.
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 600, batch = BATCH)
    fun cdQwk20OwnerWhoLeavesEndsTheOrder(helper: GameTestHelper) {
        val s = scene(helper)
        sendOutAt(helper, s.owner, s.charmander, A)
        var leftAt = -1L
        helper.onEachTick {
            val engine = TestKit.engine(helper)
            if (leftAt < 0) {
                if (engine.worker(s.charmander.uuid)?.status != WorkerStatus.WORKING) return@onEachTick
                TestKit.unlist(helper, s.owner)
                leftAt = helper.tick
            } else if (helper.tick - leftAt >= 2) {
                val claim = engine.claims.claimOf(s.charmander.uuid)
                val runs = engine.isQuickWorker(s.charmander.uuid) || claim != null
                if (runs) helper.fail("CD-QWK-20: the order or its claim $claim outlived its owner by ${helper.tick - leftAt} ticks")
            }
        }
        helper.succeedWhen { helper.assertTrue(leftAt >= 0 && helper.tick - leftAt >= 2, "the owner has not left yet") }
    }

    /**
     * A furnace with raw iron stands on a stone pillar, and its owner stands above it and sends out a Charmander onto its top. The
     * claim takes a spot on the floor beside the pillar, 2 blocks below the Charmander, so it counts as there only once it has climbed
     * down (`Arrival.isAt`). Right after the claim, the test walls the Charmander in on the furnace. The order ends once
     * `engine.travelTimeoutTicks` (600) have passed since the claim, no later than 601 ticks after it, and the owner learns that it
     * cannot reach the furnace (CD-QWK-22).
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 1_000, batch = BATCH)
    fun cdQwk22TrappedWorkerTimesOut(helper: GameTestHelper) {
        val furnace = PILLAR.above()
        helper.setBlock(PILLAR, Blocks.STONE)
        TestKit.furnace(helper, furnace, ItemStack(Items.RAW_IRON, 8))
        val owner = TestKit.listedOwner(helper, furnace.above(4))
        val charmander = TestKit.partyPokemon(owner, "charmander level=10")
        sendOutAt(helper, owner, charmander, furnace)
        var claimedAt = -1L
        var endedAt = -1L
        helper.onEachTick {
            val engine = TestKit.engine(helper)
            val tick = helper.tick
            when {
                claimedAt < 0 -> if (engine.isQuickWorker(charmander.uuid)) {
                    claimedAt = tick
                    val entity = engine.entityOf(charmander.uuid) ?: throw GameTestAssertException("the order has no entity")
                    val spot = engine.worker(charmander.uuid)?.spot ?: throw GameTestAssertException("the order has no spot")
                    val onTop = entity.blockPosition() == helper.absolutePos(furnace.above())
                    helper.assertTrue(onTop, "the Charmander is not on top of the furnace: ${entity.position()}")
                    helper.assertTrue(spot.y < entity.blockY - 1, "the spot $spot is less than 2 blocks below the Charmander")
                    for (side in listOf(furnace.east(), furnace.west(), furnace.north(), furnace.south())) {
                        helper.setBlock(side.above(), Blocks.STONE)
                        helper.setBlock(side.above(2), Blocks.STONE)
                    }
                }

                endedAt < 0 -> if (!engine.isQuickWorker(charmander.uuid)) {
                    endedAt = tick
                } else if (tick - claimedAt > 601) {
                    val status = engine.worker(charmander.uuid)?.status
                    helper.fail("CD-QWK-22: the order still runs ${tick - claimedAt} ticks after its claim, $status")
                }
            }
        }
        helper.succeedWhen {
            helper.assertTrue(endedAt >= 0, "the order has not ended yet")
            val messages = TestKit.messages(owner)
            helper.assertTrue(messages.contains("Charmander cannot reach Furnace"), "CD-QWK-22: the owner got $messages")
        }
    }

    /** On every tick, none of [pokemon] has an order or a claim of any kind, so each send-out stays a normal one. */
    private fun assertNoOrder(helper: GameTestHelper, row: String, vararg pokemon: Pokemon) {
        helper.onEachTick {
            val engine = TestKit.engine(helper)
            for (p in pokemon) {
                helper.assertFalse(engine.isQuickWorker(p.uuid), "$row: the ${p.species.name} got an order")
                val claim = engine.claims.claimOf(p.uuid)
                helper.assertTrue(claim == null, "$row: the ${p.species.name} holds $claim")
            }
        }
    }

    /** Presses R for party [slot] while [owner] looks at [rel], and checks that the send-out of [pokemon] aimed at that block. */
    private fun sendOutAt(helper: GameTestHelper, owner: ServerPlayer, pokemon: Pokemon, rel: BlockPos, slot: Int = 0) {
        TestKit.lookAt(helper, owner, rel)
        TestKit.pressSendOut(owner, slot)
        val aimed = TestKit.engine(helper).aimOf(pokemon.uuid)?.pos
        helper.assertTrue(aimed == helper.absolutePos(rel), "the send-out of the ${pokemon.species.name} aimed at $aimed, not at $rel")
    }

    /** A fake owner whom `mayInteract` refuses every block while [refuses] holds, as a protection mod refuses a player. */
    private class RefusedOwner(level: ServerLevel) : FakePlayer(level, GameProfile(UUID.randomUUID(), "refused")) {
        var refuses = true

        override fun mayInteract(level: Level, pos: BlockPos): Boolean = !refuses && super.mayInteract(level, pos)
    }

    private companion object {
        const val BATCH = "quick_work"

        /** Cobblemon ends a send-out about 31 ticks after the key press. */
        const val SEND_OUT_TICKS = 40
        val A = BlockPos(4, 2, 11)
        val B = BlockPos(4, 2, 6)
        val OWNER = BlockPos(14, 2, 11)
        val FAR = BlockPos(22, 2, 11)
        val NEAR = BlockPos(10, 2, 11)

        /** Off the line from the owner to furnace A, and within 8 blocks of it. */
        val PASTURE = BlockPos(7, 1, 15)

        /** The furnace under the roof of CD-QWK-10. */
        val PIT = BlockPos(11, 2, 11)

        /** The stone block under the furnace of CD-QWK-22. */
        val PILLAR = BlockPos(11, 2, 11)
    }
}
