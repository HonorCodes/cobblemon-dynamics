package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.config.EngineSettings
import codes.honor.cobblemondynamics.config.ServerConfig
import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.engine.WorkEngine
import codes.honor.cobblemondynamics.engine.WorkerSlot
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.leash.BindingStore
import codes.honor.cobblemondynamics.stamina.StaminaMath
import codes.honor.cobblemondynamics.stamina.TraitFlags
import codes.honor.cobblemondynamics.test.create.CreateKit
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.AfterBatch
import net.minecraft.gametest.framework.BeforeBatch
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.BlockTags
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.AbstractFurnaceBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.CropBlock
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import java.util.UUID
import kotlin.math.abs

/** Variant, Majesty, Move Mastery, Mate, rarity, Work XP, Friendship, and Slacking Off in the live engine (ADR-0023). */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class ProgressionTests {
    private var savedSettings: EngineSettings? = null

    private fun tetherConfigured(
        helper: GameTestHelper,
        pasture: PokemonPastureBlockEntity,
        owner: ServerPlayer,
        spec: String,
        configure: (Pokemon) -> Unit,
    ): PokemonEntity {
        val pokemon = TestKit.pokemon(spec)
        configure(pokemon)
        Cobblemon.storage.getPC(owner).add(pokemon)
        if (!pasture.tether(owner, pokemon, Direction.SOUTH)) throw GameTestAssertException("tether failed for $spec")
        return TestKit.pasturedEntity(helper, pasture, pokemon.uuid) ?: throw GameTestAssertException("no entity after tether for $spec")
    }

    private fun setOnlyMove(pokemon: Pokemon, moveName: String) {
        pokemon.moveSet.clear()
        pokemon.moveSet.add(Moves.getByNameOrDummy(moveName).create())
    }

    private fun bindChecked(helper: GameTestHelper, owner: ServerPlayer, entity: PokemonEntity, rel: BlockPos) {
        val bound = TestKit.bind(helper, owner, entity, rel)
        helper.assertTrue(
            bound?.target == helper.absolutePos(rel),
            "bind failed for ${entity.pokemon.species.name} at $rel: $bound (${TestKit.lastMessage(owner)})",
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun slotOf(engine: WorkEngine, pokemonId: UUID): WorkerSlot? {
        val field = WorkEngine::class.java.getDeclaredField("slots").apply { isAccessible = true }
        val map = field.get(engine) as Map<UUID, WorkerSlot>
        return map[pokemonId]
    }

    @GameTest(template = "floor_23x10x23", timeoutTicks = 800)
    fun cdPrg01Prg02Prg04Prg08GeneratorMultipliers(helper: GameTestHelper) {
        helper.assertTrue(
            abs(ServerConfig.snapshot().baseSlackChance - 0.12) < 1e-9,
            "CD-PRG-01: default baseSlackChance is ${ServerConfig.snapshot().baseSlackChance}",
        )
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(11, 1, 11), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)

        val shinyBattery = TestKit.testBattery(helper, BlockPos(6, 1, 6))
        val majesticBattery = TestKit.testBattery(helper, BlockPos(16, 1, 6))
        val zapdos50Battery = TestKit.testBattery(helper, BlockPos(6, 1, 16))
        val zapdos100Battery = TestKit.testBattery(helper, BlockPos(16, 1, 16))

        val shiny = tetherConfigured(helper, pasture, owner, "pikachu level=100") {
            it.shiny = true
            it.gender = Gender.MALE
            setOnlyMove(it, "thundershock")
        }
        val majestic = tetherConfigured(helper, pasture, owner, "pikachu level=100") {
            it.forcedAspects = setOf("majestic")
            it.gender = Gender.MALE
            setOnlyMove(it, "thundershock")
        }
        val zapdos50 = tetherConfigured(helper, pasture, owner, "zapdos level=50") {
            setOnlyMove(it, "thundershock")
        }
        val zapdos100 = tetherConfigured(helper, pasture, owner, "zapdos level=100") {
            setOnlyMove(it, "thundershock")
        }

        bindChecked(helper, owner, shiny, BlockPos(6, 1, 6))
        bindChecked(helper, owner, majestic, BlockPos(16, 1, 6))
        bindChecked(helper, owner, zapdos50, BlockPos(6, 1, 16))
        bindChecked(helper, owner, zapdos100, BlockPos(16, 1, 16))

        var majesticStartStamina = -1.0
        var majesticTicks = 0
        helper.onEachTick {
            val w = TestKit.workerOf(helper, majestic)
            if (w?.status == WorkerStatus.WORKING) {
                if (majesticStartStamina < 0.0) {
                    majesticStartStamina = w.stamina.fraction
                } else {
                    majesticTicks++
                }
            }
            if (shinyBattery.offers.isEmpty() || majesticBattery.offers.isEmpty() ||
                zapdos50Battery.offers.isEmpty() || zapdos100Battery.offers.isEmpty() || majesticTicks < 20
            ) {
                return@onEachTick
            }
            val engine = TestKit.engine(helper)
            helper.assertTrue(
                TraitFlags.has(engine.traitsOf(shiny.pokemon.uuid), TraitFlags.SHINY),
                "CD-PRG-02: Shiny flag missing",
            )
            helper.assertTrue(
                TraitFlags.has(engine.traitsOf(majestic.pokemon.uuid), TraitFlags.MAJESTIC),
                "CD-PRG-04: Majestic flag missing",
            )
            helper.assertTrue(
                shinyBattery.offers.last().offered == 5_750,
                "CD-PRG-02: Shiny Lv100 Pikachu offered ${shinyBattery.offers.last().offered}, expected 5750",
            )
            helper.assertTrue(
                majesticBattery.offers.last().offered == 6_750,
                "CD-PRG-04: Majestic Lv100 Pikachu offered ${majesticBattery.offers.last().offered}, expected 6750",
            )
            helper.assertTrue(
                zapdos50Battery.offers.last().offered == 433,
                "CD-PRG-08: Lv50 Zapdos offered ${zapdos50Battery.offers.last().offered}, expected 433",
            )
            helper.assertTrue(
                zapdos100Battery.offers.last().offered == 10_000,
                "CD-PRG-08: Lv100 Zapdos offered ${zapdos100Battery.offers.last().offered}, expected 10000",
            )
            val drained = majesticStartStamina - TestKit.workerOf(helper, majestic)!!.stamina.fraction
            val expectedDrain = majesticTicks * 0.85 / StaminaMath.capacityTicks(100, WorkEngineSettings.current)
            helper.assertTrue(
                abs(drained - expectedDrain) < 1e-9,
                "CD-PRG-04: Majestic drained $drained over $majesticTicks ticks, expected $expectedDrain",
            )
            helper.succeed()
        }
    }

    @GameTest(template = "floor_23x10x23", timeoutTicks = 800)
    fun cdPrg05Prg07MasteryAndMateGenerator(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(11, 1, 11), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)

        val maleBattery = TestKit.testBattery(helper, BlockPos(7, 1, 7))
        val femaleBattery = TestKit.testBattery(helper, BlockPos(15, 1, 7))

        val male = tetherConfigured(helper, pasture, owner, "pikachu level=100") {
            it.gender = Gender.MALE
            setOnlyMove(it, "thunderbolt")
        }
        val female = tetherConfigured(helper, pasture, owner, "pikachu level=100") {
            it.gender = Gender.FEMALE
            setOnlyMove(it, "thundershock")
        }

        bindChecked(helper, owner, male, BlockPos(7, 1, 7))
        bindChecked(helper, owner, female, BlockPos(15, 1, 7))

        helper.succeedWhen {
            val engine = TestKit.engine(helper)
            val maleFlags = engine.traitsOf(male.pokemon.uuid)
            val femaleFlags = engine.traitsOf(female.pokemon.uuid)
            helper.assertTrue(TraitFlags.has(maleFlags, TraitFlags.MASTERY), "CD-PRG-05: Thunderbolt male lacks MASTERY")
            helper.assertTrue(TraitFlags.has(maleFlags, TraitFlags.MATE), "CD-PRG-07: male Pikachu lacks MATE")
            helper.assertFalse(TraitFlags.has(femaleFlags, TraitFlags.MASTERY), "CD-PRG-05: ThunderShock female got MASTERY")
            helper.assertTrue(TraitFlags.has(femaleFlags, TraitFlags.MATE), "CD-PRG-07: female Pikachu lacks MATE")
            helper.assertTrue(maleBattery.offers.isNotEmpty() && femaleBattery.offers.isNotEmpty(), "waiting for offers")
            helper.assertTrue(
                maleBattery.offers.last().offered == 6_900,
                "CD-PRG-05/07: Mastery+Mate Lv100 Pikachu offered ${maleBattery.offers.last().offered}, expected 6900",
            )
            helper.assertTrue(
                femaleBattery.offers.last().offered == 5_750,
                "CD-PRG-07: Mate Lv100 Pikachu offered ${femaleBattery.offers.last().offered}, expected 5750",
            )
        }
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_200)
    fun cdPrg03AlphaDiglettAreaAndCapacity(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)

        val row = (5..9).map { x -> BlockPos(x, 2, 3) }
        for (crop in row) {
            helper.setBlock(crop.below(), Blocks.FARMLAND)
            helper.setBlock(crop, Blocks.WHEAT.defaultBlockState())
        }
        val target = row[2]
        val diglett = tetherConfigured(helper, pasture, owner, "diglett level=1") {
            it.isAlpha = true
            setOnlyMove(it, "scratch")
        }
        bindChecked(helper, owner, diglett, target)

        val ageAt = { rel: BlockPos -> helper.getBlockState(rel).getOptionalValue(CropBlock.AGE).orElse(-1) }
        helper.onEachTick {
            if (ageAt(target) == 0) return@onEachTick
            val grownCount = row.count { ageAt(it) > 0 }
            helper.assertTrue(grownCount == 2, "CD-PRG-03: Lv1 Alpha Diglett grew $grownCount crops on first use, expected 2")
            val worker = TestKit.workerOf(helper, diglett) ?: return@onEachTick
            val engine = TestKit.engine(helper)
            helper.assertTrue(TraitFlags.has(engine.traitsOf(diglett.pokemon.uuid), TraitFlags.ALPHA), "CD-PRG-03: ALPHA flag missing")
            val expectedAfterFirstTickAndUse = 1.0 - 101.0 / 4_500.0
            helper.assertTrue(
                abs(worker.stamina.fraction - expectedAfterFirstTickAndUse) <= 2.0 / 4_500.0,
                "CD-PRG-03: Lv1 Alpha Diglett stamina is ${worker.stamina.fraction}, expected near $expectedAfterFirstTickAndUse",
            )
            helper.succeed()
        }
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdPrg10StokerCookBoostAndScaledDrain(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)

        val furnace = TestKit.furnace(helper, BlockPos(7, 1, 3), ItemStack(Items.RAW_IRON, 2))
        val charmander = tetherConfigured(helper, pasture, owner, "charmander level=10") {
            it.isAlpha = true
            setOnlyMove(it, "flamethrower")
        }
        bindChecked(helper, owner, charmander, BlockPos(7, 1, 3))

        var workingSince = -1L
        var startStamina = -1.0
        var workingTicks = 0
        helper.onEachTick {
            val worker = TestKit.workerOf(helper, charmander) ?: return@onEachTick
            if (worker.status == WorkerStatus.WORKING) {
                if (workingSince < 0) {
                    workingSince = helper.tick
                    startStamina = worker.stamina.fraction
                } else {
                    workingTicks++
                }
            }
            if (furnace.getItem(2).count >= 1) {
                helper.assertTrue(workingSince >= 0, "furnace smelted without Stoker")
                val elapsed = helper.tick - workingSince
                helper.assertTrue(elapsed in 95..115, "CD-PRG-10: M=1.50 Stoker smelted 1 raw iron in $elapsed ticks, expected ~101")
                val drained = startStamina - worker.stamina.fraction
                val capacity = StaminaMath.capacityTicks(10, WorkEngineSettings.current, isAlpha = true)
                val expected = workingTicks * (1.0 / 1.5) / capacity
                helper.assertTrue(
                    abs(drained - expected) <= 2.0 / capacity,
                    "CD-PRG-10: Stoker drained $drained over $workingTicks ticks, expected $expected",
                )
                helper.succeed()
            }
        }
    }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_800)
    fun cdPrg11Prg12Prg13WorkXpZeroEvsAndFriendship(helper: GameTestHelper) {
        val owner = TestKit.listedOwner(helper, BlockPos(9, 2, 7))
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        TestKit.testBattery(helper, BlockPos(7, 1, 3))

        val pikachu = tetherConfigured(helper, pasture, owner, "pikachu level=5") {
            it.setFriendship(50, true)
            setOnlyMove(it, "thundershock")
        }
        bindChecked(helper, owner, pikachu, BlockPos(7, 1, 3))

        val initialXp = pikachu.pokemon.experience
        var primedNear = false
        var primedFar = false
        var workingTicks = 0
        val permanentStats = listOf(
            Stats.HP,
            Stats.ATTACK,
            Stats.DEFENCE,
            Stats.SPECIAL_ATTACK,
            Stats.SPECIAL_DEFENCE,
            Stats.SPEED,
        )

        helper.onEachTick {
            val engine = TestKit.engine(helper)
            val worker = engine.worker(pikachu.pokemon.uuid) ?: return@onEachTick
            if (worker.status != WorkerStatus.WORKING) return@onEachTick
            workingTicks++
            val slot = slotOf(engine, pikachu.pokemon.uuid) ?: return@onEachTick

            if (!primedNear && workingTicks == 5) {
                slot.friendshipTicks = 11_995
                primedNear = true
            }
            if (primedNear && !primedFar && pikachu.pokemon.friendship == 52) {
                TestKit.place(owner, owner.x + 40.0, owner.y, owner.z)
                slot.friendshipTicks = 11_995
                primedFar = true
            }

            if (workingTicks >= 1_205) {
                val gainedXp = pikachu.pokemon.experience - initialXp
                helper.assertTrue(gainedXp == 4, "CD-PRG-11: Lv5 Pikachu gained $gainedXp XP after 1200 Working ticks, expected 4")
                for (stat in permanentStats) {
                    val ev = pikachu.pokemon.evs.getOrDefault(stat)
                    helper.assertTrue(ev == 0, "CD-PRG-12: Work XP added $ev EVs to $stat")
                }
                helper.assertTrue(
                    pikachu.pokemon.friendship == 53,
                    "CD-PRG-13: Pikachu friendship is ${pikachu.pokemon.friendship}, expected 53 (50 + 2 near + 1 far)",
                )
                helper.succeed()
            }
        }
    }

    @GameTest(template = "floor_23x10x23", timeoutTicks = 900, batch = SLACK_BATCH)
    fun cdPrg14Prg16WheelDriverAndStokerSlackAndResume(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 9), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)

        val wheel = CreateKit.smallWheel(helper, BlockPos(4, 2, 5))
        val furnace = TestKit.furnace(helper, BlockPos(10, 1, 5), ItemStack(Items.RAW_IRON, 16))
        val squirtle = tetherConfigured(helper, pasture, owner, "squirtle level=10 nature=relaxed") {
            it.setFriendship(0, true)
            setOnlyMove(it, "watergun")
        }
        val charmander = tetherConfigured(helper, pasture, owner, "charmander level=10 nature=relaxed") {
            it.setFriendship(0, true)
            setOnlyMove(it, "ember")
        }
        bindChecked(helper, owner, squirtle, BlockPos(4, 2, 5))
        bindChecked(helper, owner, charmander, BlockPos(10, 1, 5))

        TestKit.furnace(helper, BlockPos(19, 2, 5), ItemStack(Items.RAW_IRON, 16))
        val quickOwner = TestKit.listedOwner(helper, BlockPos(19, 2, 11), "quick_owner")
        val quickCharmander = TestKit.partyPokemon(quickOwner, "charmander level=10 nature=relaxed")
        quickCharmander.setFriendship(0, true)
        TestKit.lookAt(helper, quickOwner, BlockPos(19, 2, 5))
        TestKit.pressSendOut(quickOwner)

        var squirtleSlacked = false
        var squirtleStoppedWheel = false
        var squirtleStaminaAtSlack = -1.0
        var squirtleResumed = false

        var charmanderSlacked = false
        var charmanderUnlitInSlack = false
        var charmanderStaminaAtSlack = -1.0
        var charmanderResumed = false

        var quickWorkingTicks = 0

        helper.onEachTick {
            val engine = TestKit.engine(helper)
            val quickWorker = engine.worker(quickCharmander.uuid)
            if (quickWorker != null) {
                helper.assertFalse(
                    quickWorker.status == WorkerStatus.SLACKING,
                    "CD-PRG-14: Quick Worker entered SLACKING",
                )
                if (quickWorker.status == WorkerStatus.WORKING) quickWorkingTicks++
            }

            val sw = TestKit.workerOf(helper, squirtle)
            if (sw != null) {
                if (sw.status == WorkerStatus.SLACKING) {
                    if (!squirtleSlacked) {
                        squirtleSlacked = true
                        squirtleStaminaAtSlack = sw.stamina.fraction
                    }
                    helper.assertTrue(
                        TestKit.claimOf(helper, squirtle)?.kind == ClaimKind.WORK,
                        "CD-PRG-16: Squirtle lost WORK claim while SLACKING",
                    )
                    helper.assertTrue(BindingStore.read(squirtle.pokemon) != null, "CD-PRG-16: Squirtle lost binding while SLACKING")
                    helper.assertTrue(
                        abs(sw.stamina.fraction - squirtleStaminaAtSlack) < 1e-12,
                        "CD-PRG-16: Squirtle stamina changed during SLACKING: ${sw.stamina.fraction} vs $squirtleStaminaAtSlack",
                    )
                    if (wheel.getSpeed() == 0f) squirtleStoppedWheel = true
                } else if (squirtleSlacked && sw.status == WorkerStatus.WORKING && wheel.getSpeed() == 8f) {
                    squirtleResumed = true
                }
            }

            val cw = TestKit.workerOf(helper, charmander)
            if (cw != null) {
                val lit = helper.level.getBlockState(furnace.blockPos).getValue(AbstractFurnaceBlock.LIT)
                if (cw.status == WorkerStatus.SLACKING) {
                    if (!charmanderSlacked) {
                        charmanderSlacked = true
                        charmanderStaminaAtSlack = cw.stamina.fraction
                    }
                    helper.assertTrue(
                        TestKit.claimOf(helper, charmander)?.kind == ClaimKind.WORK,
                        "CD-PRG-16: Charmander lost WORK claim while SLACKING",
                    )
                    helper.assertTrue(BindingStore.read(charmander.pokemon) != null, "CD-PRG-16: Charmander lost binding while SLACKING")
                    helper.assertTrue(
                        abs(cw.stamina.fraction - charmanderStaminaAtSlack) < 1e-12,
                        "CD-PRG-16: Charmander stamina changed during SLACKING: ${cw.stamina.fraction} vs $charmanderStaminaAtSlack",
                    )
                    if (!lit) charmanderUnlitInSlack = true
                } else if (charmanderSlacked && cw.status == WorkerStatus.WORKING && lit) {
                    charmanderResumed = true
                }
            }

            if (squirtleStoppedWheel && squirtleResumed && charmanderUnlitInSlack && charmanderResumed && quickWorkingTicks >= 120) {
                helper.succeed()
            }
        }
    }

    @GameTest(template = "floor_23x17x23", timeoutTicks = 900, batch = SLACK_BATCH)
    fun cdPrg14LumberjackDefersSlackMidFell(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(11, 1, 15), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)

        val base = BlockPos(11, 2, 9)
        helper.setBlock(base.below(), Blocks.DIRT)
        val logs = (0 until 9).map { base.above(it) }
        for (log in logs) helper.setBlock(log, Blocks.OAK_LOG)
        fun layer(y: Int, radius: Int, round: Boolean) {
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    val pos = BlockPos(base.x + dx, y, base.z + dz)
                    if ((round && abs(dx) == radius && abs(dz) == radius) || pos in logs) continue
                    helper.setBlock(pos, Blocks.OAK_LEAVES.defaultBlockState())
                }
            }
        }
        for (y in 5..7) layer(y, 3, round = false)
        layer(8, 3, round = true)
        layer(9, 2, round = true)

        val topLeaf = BlockPos(base.x, 9, base.z + 2)
        val scyther = tetherConfigured(helper, pasture, owner, "scyther level=60 nature=relaxed") {
            it.setFriendship(0, true)
            setOnlyMove(it, "scratch")
        }
        val bound = TestKit.bind(helper, owner, scyther, base.above(2))
        helper.assertTrue(bound?.roleId == id("lumberjack"), "Scyther bound as ${bound?.roleId}")

        var sawMidFellWorking = false
        helper.onEachTick {
            val worker = TestKit.workerOf(helper, scyther) ?: return@onEachTick
            val baseState = helper.getBlockState(base)
            if (baseState.`is`(Blocks.OAK_LOG) && helper.getBlockState(topLeaf).isAir) {
                sawMidFellWorking = true
                helper.assertTrue(
                    worker.status == WorkerStatus.WORKING,
                    "CD-PRG-14: Scyther became ${worker.status} mid-fell while base log still stood",
                )
            }
            if (!baseState.`is`(BlockTags.LOGS)) {
                helper.assertTrue(sawMidFellWorking, "fell completed in one tick instead of spanning the slack check")
                helper.assertTrue(baseState.`is`(Blocks.OAK_SAPLING), "CD-LUM-17: base is $baseState after fell, not oak sapling")
                helper.assertTrue(
                    worker.status == WorkerStatus.SLACKING,
                    "CD-PRG-14: deferred slack did not trigger on fell completion; status is ${worker.status}",
                )
                helper.assertTrue(TestKit.claimOf(helper, scyther)?.kind == ClaimKind.WORK, "Scyther lost claim on slack")
                helper.succeed()
            }
        }
    }

    @BeforeBatch(batch = SLACK_BATCH)
    fun enableDeterministicSlack(@Suppress("UNUSED_PARAMETER") level: ServerLevel) {
        val current = WorkEngineSettings.current
        savedSettings = current
        WorkEngineSettings.current = current.copy(
            baseSlackChance = 1.0,
            slackCheckTicks = 100,
            slackDurationTicks = 40,
        )
    }

    @AfterBatch(batch = SLACK_BATCH)
    fun restoreSlackSettings(@Suppress("UNUSED_PARAMETER") level: ServerLevel) {
        WorkEngineSettings.current = savedSettings ?: EngineSettings.DEFAULT.copy(baseSlackChance = 0.0)
        savedSettings = null
    }

    private companion object {
        const val SLACK_BATCH = "progression_slack"
    }
}
