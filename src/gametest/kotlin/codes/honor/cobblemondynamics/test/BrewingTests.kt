package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.id
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.item.alchemy.Potions
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/**
 * The Brew Stoker and the Brewer on real brewing stands. The stand ticks before the work engine, so a test that reads the stand in
 * the GameTest ticker sees the engine's write of the same tick.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class BrewingTests {
    private val standRel = BlockPos(7, 1, 3)

    private fun pasture(helper: GameTestHelper, rel: BlockPos = BlockPos(7, 1, 7)): Pair<ServerPlayer, PokemonPastureBlockEntity> {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, rel, owner)
        TestKit.workMode(pasture, true)
        return owner to pasture
    }

    /** A brewing stand with three water bottles, [ingredient] and [fuel] charges of its own. */
    private fun stand(helper: GameTestHelper, rel: BlockPos, ingredient: ItemStack?, fuel: Int): BrewingStandBlockEntity {
        helper.setBlock(rel, Blocks.BREWING_STAND)
        val stand = helper.getBlockEntity<BrewingStandBlockEntity>(rel)
        repeat(BOTTLE_SLOTS) { stand.setItem(it, PotionContents.createItemStack(Items.POTION, Potions.WATER)) }
        ingredient?.let { stand.setItem(INGREDIENT_SLOT, it) }
        stand.fuel = fuel
        return stand
    }

    private fun brewedAwkward(stand: BrewingStandBlockEntity): Boolean = (0 until BOTTLE_SLOTS).all {
        stand.getItem(it).get(DataComponents.POTION_CONTENTS)?.`is`(Potions.AWKWARD) == true
    }

    private fun roleOf(helper: GameTestHelper, entity: PokemonEntity) = TestKit.engine(helper).roleOf(entity.pokemon.uuid)?.definition?.id

    /**
     * A Fire-type claims a stand with three water bottles and a nether wart, and no blaze powder, as the Brew Stoker (CD-BRF-01). The
     * stand holds fuel while it Works and the brew completes (CD-BRF-02). With the wart used up, nothing can brew, so the worker is in
     * Standby within 20 ticks (CD-BRF-04) and the stand has its own fuel of 0 back (CD-BRF-05).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_200)
    fun cdBrf02BrewStokerBrewsWithNoBlazePowder(helper: GameTestHelper) {
        val (owner, pasture) = pasture(helper)
        val stand = stand(helper, standRel, ItemStack(Items.NETHER_WART), fuel = 0)
        val charmander = TestKit.tether(helper, pasture, owner, "charmander level=10")
        var worked = false
        var brewedAt = -1L
        helper.onEachTick {
            val worker = TestKit.workerOf(helper, charmander) ?: return@onEachTick
            helper.assertTrue(stand.getItem(FUEL_SLOT).isEmpty, "the fuel slot holds ${stand.getItem(FUEL_SLOT)}")
            if (brewedAt < 0) {
                if (worker.status == WorkerStatus.WORKING) {
                    worked = true
                    helper.assertTrue(
                        roleOf(helper, charmander) == id("brew_stoker"),
                        "CD-BRF-01: the role is ${roleOf(helper, charmander)}",
                    )
                    helper.assertTrue(stand.fuel >= 1, "CD-BRF-02: the Worked stand holds ${stand.fuel} fuel")
                }
                if (brewedAwkward(stand)) {
                    helper.assertTrue(worked, "the stand brewed with no Brew Stoker")
                    brewedAt = helper.tick
                }
                return@onEachTick
            }
            if (worker.status == WorkerStatus.STANDBY) {
                helper.assertTrue(stand.fuel == 0, "CD-BRF-05: the stopped Brew Stoker left ${stand.fuel} fuel")
                helper.succeed()
            } else {
                helper.assertTrue(helper.tick - brewedAt < 20, "CD-BRF-04: the Brew Stoker is ${worker.status} 20 ticks after the brew")
            }
        }
    }

    /**
     * The row's literal case. A stand with three water bottles, a nether wart and fuel 0 is worked by a Brew Stoker, which gives it fuel
     * 1 on its first Working tick. The test releases the claim on that tick, before the stand's next tick can start the brew: the stand
     * reads fuel 0 at once and on the next tick, and no brew starts (CD-BRF-05).
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 600)
    fun cdBrf05ReleaseBeforeABrewLeavesFuel0(helper: GameTestHelper) {
        val (owner, pasture) = pasture(helper)
        val stand = stand(helper, standRel, ItemStack(Items.NETHER_WART), fuel = 0)
        val charmander = TestKit.tether(helper, pasture, owner, "charmander level=10")
        var releasedAt = -1L
        helper.onEachTick {
            val worker = TestKit.workerOf(helper, charmander) ?: return@onEachTick
            if (releasedAt < 0) {
                if (worker.status != WorkerStatus.WORKING) {
                    helper.assertTrue(stand.fuel == 0 && stand.brewTime == 0, "the stand has fuel ${stand.fuel} before any work")
                    return@onEachTick
                }
                helper.assertTrue(roleOf(helper, charmander) == id("brew_stoker"), "the role is ${roleOf(helper, charmander)}")
                helper.assertTrue(
                    stand.fuel >= 1 && stand.brewTime == 0,
                    "the first Working tick left fuel ${stand.fuel}, brew ${stand.brewTime}",
                )
                TestKit.release(helper, charmander, stand.blockPos)
                helper.assertTrue(stand.fuel == 0, "CD-BRF-05: the released Brew Stoker left fuel ${stand.fuel}")
                releasedAt = helper.tick
                return@onEachTick
            }
            helper.assertTrue(
                stand.fuel == 0,
                "CD-BRF-05: ${helper.tick - releasedAt} ticks after the release the stand has fuel ${stand.fuel}",
            )
            helper.assertTrue(stand.brewTime == 0, "a brew started after the release: brew time ${stand.brewTime}")
            if (helper.tick - releasedAt >= 2) helper.succeed()
        }
    }

    /**
     * Decision 20 of the 1.2.1 plan. Vanilla turns a blaze powder into 20 fuel whenever the stand's fuel is 0, so a stand with powder
     * fuels itself. The Brew Stoker has demand only while a brew waits for fuel (CD-BRF-04), so it claims the stand (CD-BRF-01) and never
     * Works through the 3 brews of the test mod's stick recipe. It adds no fuel and takes no powder (CD-BRF-03): the stand spends 3 of the
     * powder's 20.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 2_200)
    fun cdBrf03StandWithBlazePowderFuelsItself(helper: GameTestHelper) {
        val (owner, pasture) = pasture(helper)
        val stand = stand(helper, standRel, ItemStack(Items.STICK, 3), fuel = 0)
        stand.setItem(FUEL_SLOT, ItemStack(Items.BLAZE_POWDER))
        val charmander = TestKit.tether(helper, pasture, owner, "charmander level=10")
        var claimed = false
        helper.onEachTick {
            val worker = TestKit.workerOf(helper, charmander) ?: return@onEachTick
            helper.assertTrue(
                worker.status != WorkerStatus.WORKING,
                "CD-BRF-04: the Brew Stoker Works at fuel ${stand.fuel} with brew time ${stand.brewTime}",
            )
            if (TestKit.claimOf(helper, charmander)?.kind == ClaimKind.WORK) claimed = true
            if (stand.getItem(INGREDIENT_SLOT).isEmpty && stand.brewTime == 0) {
                helper.assertTrue(claimed, "CD-BRF-01: the Charmander never claimed the stand")
                helper.assertTrue(stand.fuel == 17, "CD-BRF-03: three brews left the stand at fuel ${stand.fuel}, not 17")
                helper.succeed()
            }
        }
    }

    /**
     * A Poison-type claims the stand in its work area as the Brewer (CD-BRW-01) and waits in Standby while no brew runs (CD-BRW-03).
     * The nether wart goes into both stands so that the brew starts on the tick of the Brewer's next Standby check (CD-ASG-12), and
     * the Brewer works it from its first tick. That brew takes 200 ticks (CD-BRW-02), and the other stand, outside the work area, takes
     * vanilla's 400. Both stands end with the same fuel (CD-BRW-04).
     */
    @GameTest(template = "floor_23x10x23", timeoutTicks = 1_400)
    fun cdBrw02WorkedBrewTakes200Ticks(helper: GameTestHelper) {
        val (owner, pasture) = pasture(helper, BlockPos(7, 1, 11))
        val worked = stand(helper, BlockPos(10, 1, 11), null, fuel = 20)
        val alone = stand(helper, BlockPos(20, 1, 11), null, fuel = 20)
        val ekans = TestKit.tether(helper, pasture, owner, "ekans level=20")
        var step = "claim"
        var workedStart = -1L
        var aloneStart = -1L
        var workedDone = -1L
        helper.onEachTick {
            val worker = TestKit.workerOf(helper, ekans) ?: return@onEachTick
            val now = helper.tick
            when (step) {
                "claim" -> if (worker.status == WorkerStatus.STANDBY) {
                    val claim = TestKit.claimOf(helper, ekans)
                    helper.assertTrue(
                        claim?.kind == ClaimKind.WORK && claim.key == TestKit.keyOf(helper, worked.blockPos),
                        "CD-BRW-01: the Ekans holds $claim",
                    )
                    helper.assertTrue(roleOf(helper, ekans) == id("brewer"), "CD-BRW-01: the role is ${roleOf(helper, ekans)}")
                    step = "wait"
                } else {
                    helper.assertTrue(worker.status != WorkerStatus.WORKING, "CD-BRW-03: the Ekans Works a stand with no brew")
                }

                "wait" -> {
                    helper.assertTrue(worker.status == WorkerStatus.STANDBY, "CD-BRW-03: the Ekans is ${worker.status} with no brew")
                    if ((helper.level.gameTime + 1 - worker.statusSince) % STANDBY_CHECK_TICKS == 0L) {
                        worked.setItem(INGREDIENT_SLOT, ItemStack(Items.NETHER_WART))
                        alone.setItem(INGREDIENT_SLOT, ItemStack(Items.NETHER_WART))
                        step = "brew"
                    }
                }

                "brew" -> {
                    if (workedStart < 0 && worked.brewTime > 0) {
                        workedStart = now
                        helper.assertTrue(
                            worker.status == WorkerStatus.WORKING && worked.brewTime == 399,
                            "the Brewer did not work the brew from its start: ${worker.status}, brew time ${worked.brewTime}",
                        )
                    }
                    if (aloneStart < 0 && alone.brewTime > 0) aloneStart = now
                    if (workedDone < 0 && workedStart >= 0 && brewedAwkward(worked)) {
                        workedDone = now
                        helper.assertTrue(now - workedStart in 198L..202L, "CD-BRW-02: the worked brew took ${now - workedStart} ticks")
                    }
                    if (workedDone >= 0 && worker.status != WorkerStatus.STANDBY) {
                        helper.assertTrue(now - workedDone < 20, "CD-BRW-03: the Brewer is ${worker.status} 20 ticks after its brew")
                    }
                    if (aloneStart >= 0 && brewedAwkward(alone)) {
                        helper.assertTrue(workedDone >= 0, "the stand with no Brewer brewed first")
                        helper.assertTrue(now - aloneStart == 400L, "the stand with no Brewer took ${now - aloneStart} ticks")
                        helper.assertTrue(
                            worked.fuel == alone.fuel,
                            "CD-BRW-04: fuel ${worked.fuel} with the Brewer, ${alone.fuel} without",
                        )
                        helper.succeed()
                    }
                }
            }
        }
    }

    /**
     * A server stop ends the Brew Stoker's work as a release does, before the levels save, so the saved stand keeps no fuel that the
     * worker gave (CD-BRF-05). The stop reaches every worker in the engine, so the test has its own batch.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_200, batch = "server_stop")
    fun cdBrf05ServerStopLeavesNoFuel(helper: GameTestHelper) {
        val (owner, pasture) = pasture(helper)
        val stand = stand(helper, standRel, ItemStack(Items.NETHER_WART), fuel = 0)
        val charmander = TestKit.tether(helper, pasture, owner, "charmander level=10")
        helper.succeedWhen {
            val status = TestKit.workerOf(helper, charmander)?.status
            helper.assertTrue(status == WorkerStatus.WORKING && stand.fuel >= 1, "the Brew Stoker is $status at fuel ${stand.fuel}")
            TestKit.engine(helper).stopWorkingHandlers()
            helper.assertTrue(stand.fuel == 0, "CD-BRF-05: the server stop left ${stand.fuel} fuel")
        }
    }

    private companion object {
        const val BOTTLE_SLOTS = 3
        const val INGREDIENT_SLOT = 3
        const val FUEL_SLOT = 4

        /** A worker in Standby checks for demand on every 20th tick of it (CD-ASG-12). */
        const val STANDBY_CHECK_TICKS = 20L
    }
}
