package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.config.EngineSettings
import codes.honor.cobblemondynamics.config.ServerConfig
import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.engine.PastureRuntime
import codes.honor.cobblemondynamics.engine.ScanScheduler
import codes.honor.cobblemondynamics.engine.WorkArea
import codes.honor.cobblemondynamics.engine.WorkEngine
import codes.honor.cobblemondynamics.engine.WorkerSlot
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.leash.BindingStore
import codes.honor.cobblemondynamics.pasture.PastureAdapter
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
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.inventory.EnchantmentMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.AbstractFurnaceBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.CropBlock
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import java.util.UUID
import kotlin.math.abs

/** Variant, Majesty, Move Mastery, Mate, rarity, Work XP, Friendship, and Slacking Off in the live engine (ADR-0023). */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class ProgressionTests {
    private var savedSettings: EngineSettings? = null
    private var savedTightSettings: EngineSettings? = null

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

    private fun openMenu(helper: GameTestHelper, player: ServerPlayer, tableRel: BlockPos): EnchantmentMenu {
        val menu = EnchantmentMenu(1, player.inventory, ContainerLevelAccess.create(helper.level, helper.absolutePos(tableRel)))
        player.containerMenu = menu
        NeoForge.EVENT_BUS.post(PlayerContainerEvent.Open(player, menu))
        TestKit.afterTest(helper) { closeMenu(player, menu) }
        return menu
    }

    private fun closeMenu(player: ServerPlayer, menu: EnchantmentMenu) {
        if (player.containerMenu !== menu) return
        NeoForge.EVENT_BUS.post(PlayerContainerEvent.Close(player, menu))
        player.containerMenu = player.inventoryMenu
    }

    private fun offers(menu: EnchantmentMenu): List<Int> = menu.costs.toList() + menu.enchantClue.toList() + menu.levelClue.toList()

    private fun countItem(chest: ChestBlockEntity, item: net.minecraft.world.item.Item): Int =
        (0 until chest.containerSize).sumOf { idx -> chest.getItem(idx).takeIf { it.`is`(item) }?.count ?: 0 }

    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_800)
    fun cdPrg11ExhaustionTickStillGrantsWorkXp(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        TestKit.testBattery(helper, BlockPos(5, 1, 3))
        TestKit.testBattery(helper, BlockPos(9, 1, 3))

        val lv5 = tetherConfigured(helper, pasture, owner, "pikachu level=5") {
            setOnlyMove(it, "thundershock")
        }
        val capped = tetherConfigured(helper, pasture, owner, "pikachu level=100") {
            setOnlyMove(it, "thundershock")
        }
        bindChecked(helper, owner, lv5, BlockPos(5, 1, 3))
        bindChecked(helper, owner, capped, BlockPos(9, 1, 3))

        val initialXp = lv5.pokemon.experience
        var primed = false
        var cappedWorkingTicks = 0

        helper.onEachTick {
            val engine = TestKit.engine(helper)
            val cappedWorker = engine.worker(capped.pokemon.uuid)
            val cappedSlot = slotOf(engine, capped.pokemon.uuid)
            if (cappedWorker?.status == WorkerStatus.WORKING && cappedSlot != null) {
                cappedWorkingTicks++
                helper.assertTrue(
                    cappedSlot.xpTicks == 0L,
                    "CD-PRG-11: level-capped worker banked ${cappedSlot.xpTicks} xpTicks",
                )
            }

            val worker = engine.worker(lv5.pokemon.uuid) ?: return@onEachTick
            val slot = slotOf(engine, lv5.pokemon.uuid) ?: return@onEachTick
            if (!primed && worker.status == WorkerStatus.WORKING) {
                val remaining = (1_200L - slot.xpTicks).coerceAtLeast(1L)
                worker.stamina.fraction = remaining.toDouble() / StaminaMath.capacityTicks(5, WorkEngineSettings.current)
                primed = true
                return@onEachTick
            }
            if (primed && worker.tired) {
                helper.assertTrue(worker.status.isResting, "exhausted worker did not enter rest: ${worker.status}")
                val gained = lv5.pokemon.experience - initialXp
                helper.assertTrue(
                    gained == 4,
                    "CD-PRG-11: worker exhausting stamina on tick 1200 gained $gained XP, expected 4",
                )
                helper.assertTrue(cappedWorkingTicks >= 10, "capped worker worked only $cappedWorkingTicks ticks")
                helper.succeed()
            }
        }
    }

    @GameTest(template = "floor_23x10x23", timeoutTicks = 900, batch = SLACK_BATCH)
    fun cdPrg16MuseLosesOpenMenuOffersOnFirstSlackTick(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(11, 1, 11), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)

        val tableRel = BlockPos(11, 2, 6)
        val shelvedRel = BlockPos(17, 2, 17)
        val bareRel = BlockPos(5, 2, 17)
        helper.setBlock(tableRel, Blocks.ENCHANTING_TABLE)
        helper.setBlock(shelvedRel, Blocks.ENCHANTING_TABLE)
        helper.setBlock(bareRel, Blocks.ENCHANTING_TABLE)
        for (x in -2..2) helper.setBlock(shelvedRel.offset(x, 0, -2), Blocks.BOOKSHELF)
        for (z in -1..1) helper.setBlock(shelvedRel.offset(-2, 0, z), Blocks.BOOKSHELF)

        val clefairy = tetherConfigured(helper, pasture, owner, "clefairy level=50 nature=relaxed") {
            it.setFriendship(0, true)
            setOnlyMove(it, "pound")
        }
        bindChecked(helper, owner, clefairy, tableRel)

        val enchanter = TestKit.owner(helper, "muse_enchanter")
        val shelvedRef = EnchantmentMenu(2, enchanter.inventory, ContainerLevelAccess.create(helper.level, helper.absolutePos(shelvedRel)))
        shelvedRef.getSlot(0).set(ItemStack(Items.DIAMOND_PICKAXE))
        val bareRef = EnchantmentMenu(3, enchanter.inventory, ContainerLevelAccess.create(helper.level, helper.absolutePos(bareRel)))
        bareRef.getSlot(0).set(ItemStack(Items.DIAMOND_PICKAXE))
        val shelvedOffers = offers(shelvedRef)
        val bareOffers = offers(bareRef)
        helper.assertTrue(shelvedOffers != bareOffers, "8-shelf reference offers equal bare offers: $shelvedOffers")

        var menu: EnchantmentMenu? = null
        var verifiedWorking = false
        var sawSlack = false

        helper.onEachTick {
            val worker = TestKit.workerOf(helper, clefairy) ?: return@onEachTick
            val open = menu
            if (open == null) {
                val claim = TestKit.claimOf(helper, clefairy)
                if (claim?.kind == ClaimKind.WORK && worker.status == WorkerStatus.STANDBY) {
                    menu = openMenu(helper, enchanter, tableRel).also { it.getSlot(0).set(ItemStack(Items.DIAMOND_PICKAXE)) }
                }
                return@onEachTick
            }
            if (!sawSlack && worker.status == WorkerStatus.WORKING) {
                helper.assertTrue(
                    offers(open) == shelvedOffers,
                    "CD-MUS-03: Working Muse offers ${offers(open)}, expected $shelvedOffers",
                )
                verifiedWorking = true
            } else if (worker.status == WorkerStatus.SLACKING) {
                helper.assertTrue(verifiedWorking, "Muse entered SLACKING before WORKING offers were verified")
                if (!sawSlack) {
                    sawSlack = true
                    helper.assertTrue(
                        offers(open) == bareOffers,
                        "CD-PRG-16: open EnchantmentMenu kept stale Muse offers on first SLACKING tick: ${offers(open)} vs $bareOffers",
                    )
                }
                helper.assertTrue(TestKit.claimOf(helper, clefairy)?.kind == ClaimKind.WORK, "Muse lost WORK claim while SLACKING")
                helper.assertTrue(
                    offers(open) == bareOffers,
                    "CD-PRG-16: open EnchantmentMenu offers during SLACKING are ${offers(open)}, expected $bareOffers",
                )
            } else if (sawSlack && worker.status == WorkerStatus.WORKING) {
                helper.assertTrue(
                    offers(open) == shelvedOffers,
                    "CD-PRG-16: open EnchantmentMenu did not regain Muse offers when WORKING resumed: ${offers(open)} vs $shelvedOffers",
                )
                closeMenu(enchanter, open)
                helper.succeed()
            }
        }
    }

    @GameTest(template = "floor_23x10x23", timeoutTicks = 900, batch = SLACK_BATCH)
    fun cdPrg16SlackEndedRechecksRevokedLandPermissions(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(11, 1, 11), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)

        val furnaceRel = BlockPos(7, 1, 7)
        val furnace = TestKit.furnace(helper, furnaceRel, ItemStack(Items.RAW_IRON, 16))

        val sourceRel = BlockPos(15, 2, 7)
        val destRel = BlockPos(15, 2, 11)
        helper.setBlock(sourceRel, Blocks.CHEST)
        val sourceChest = helper.getBlockEntity<ChestBlockEntity>(sourceRel)
        for (slot in 0 until 15) sourceChest.setItem(slot, ItemStack(Items.COBBLESTONE, 64))
        helper.setBlock(destRel, Blocks.CHEST)
        val destChest = helper.getBlockEntity<ChestBlockEntity>(destRel)
        val destAbs = helper.absolutePos(destRel)

        TestKit.afterTest(helper) { TestLandClaims.refused.removeAll(setOf(furnace.blockPos, destAbs)) }

        val charmander = tetherConfigured(helper, pasture, owner, "charmander level=10 nature=relaxed") {
            it.setFriendship(0, true)
            setOnlyMove(it, "ember")
        }
        val alakazam = tetherConfigured(helper, pasture, owner, "alakazam level=10 nature=relaxed") {
            it.setFriendship(0, true)
            setOnlyMove(it, "teleport")
        }
        bindChecked(helper, owner, charmander, furnaceRel)
        val transferBinding = TestKit.bindTransfer(helper, owner, alakazam, sourceRel, destRel)
        helper.assertTrue(transferBinding?.destination == destAbs, "Alakazam transfer bind failed: $transferBinding")

        val pollPhase = ScanScheduler.phaseFor(pasture.blockPos.asLong(), PastureRuntime.POLL_TICKS)
        var cEndTick = -1L
        var aEndTick = -1L
        var cobbleAtRevoke = -1
        var cDone = false
        var aDone = false

        helper.onEachTick {
            val cw = TestKit.workerOf(helper, charmander)
            val aw = TestKit.workerOf(helper, alakazam)
            val now = helper.level.gameTime
            val duration = WorkEngineSettings.current.slackDurationTicks.toLong()

            if (cw != null) {
                if (cEndTick < 0L && cw.status == WorkerStatus.SLACKING) {
                    val base = maxOf(now + 4L, if (aEndTick > 0L) aEndTick + 4L else 0L)
                    cEndTick = base + ((pollPhase + 6L - base).mod(20L))
                    cw.statusSince = cEndTick - duration
                } else if (cEndTick > 0L) {
                    if (now == cEndTick - 1L) {
                        TestLandClaims.refused += furnace.blockPos
                    } else if (now >= cEndTick) {
                        helper.assertFalse(
                            cw.status == WorkerStatus.WORKING,
                            "CD-PRG-16: Stoker resumed WORKING at $now after target permission was revoked during SLACKING",
                        )
                        helper.assertFalse(
                            helper.level.getBlockState(furnace.blockPos).getValue(AbstractFurnaceBlock.LIT),
                            "Furnace re-lit after target permission was revoked",
                        )
                        if (now >= cEndTick + 2L && !cDone) {
                            helper.assertTrue(cw.status == WorkerStatus.IDLE, "Stoker status at cEndTick+2 is ${cw.status}, expected IDLE")
                            helper.assertTrue(TestKit.claimOf(helper, charmander) == null, "Stoker kept claim after revoked target")
                            helper.assertTrue(BindingStore.read(charmander.pokemon) == null, "Stoker binding remained after revoked target")
                            cDone = true
                        }
                    }
                }
            }

            if (aw != null) {
                if (aEndTick < 0L && aw.status == WorkerStatus.SLACKING) {
                    val base = maxOf(now + 4L, if (cEndTick > 0L) cEndTick + 4L else 0L)
                    aEndTick = base + ((pollPhase + 11L - base).mod(20L))
                    aw.statusSince = aEndTick - duration
                } else if (aEndTick > 0L) {
                    if (now == aEndTick - 1L) {
                        cobbleAtRevoke = countItem(destChest, Items.COBBLESTONE)
                        helper.assertTrue(cobbleAtRevoke >= 1, "Alakazam moved no cobblestone before SLACKING")
                        TestLandClaims.refused += destAbs
                    } else if (now >= aEndTick) {
                        helper.assertFalse(
                            aw.status == WorkerStatus.WORKING,
                            "CD-PRG-16: Telekinesis resumed WORKING at $now after destination permission was revoked during SLACKING",
                        )
                        val currentCobble = countItem(destChest, Items.COBBLESTONE)
                        helper.assertTrue(
                            currentCobble == cobbleAtRevoke,
                            "CD-PRG-16: Telekinesis moved cobblestone after destination revocation ($currentCobble vs $cobbleAtRevoke)",
                        )
                        if (now >= aEndTick + 2L && !aDone) {
                            helper.assertTrue(
                                aw.status == WorkerStatus.IDLE,
                                "Telekinesis status at aEndTick+2 is ${aw.status}, expected IDLE",
                            )
                            helper.assertTrue(TestKit.claimOf(helper, alakazam) == null, "Telekinesis kept claim after revoked destination")
                            helper.assertTrue(
                                BindingStore.read(alakazam.pokemon) == null,
                                "Telekinesis binding remained after revoked destination",
                            )
                            aDone = true
                        }
                    }
                }
            }

            if (cDone && aDone) {
                helper.succeed()
            }
        }
    }

    @GameTest(template = "floor_23x10x23", timeoutTicks = 900, batch = TIGHT_SLACK_BATCH)
    fun cdPrg16SlackingWanderStaysInsideTightWorkArea(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(11, 1, 11), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)

        TestKit.furnace(helper, BlockPos(11, 1, 10), ItemStack(Items.RAW_IRON, 16))
        val charmander = tetherConfigured(helper, pasture, owner, "charmander level=10 nature=relaxed") {
            it.setFriendship(0, true)
            setOnlyMove(it, "ember")
        }
        bindChecked(helper, owner, charmander, BlockPos(11, 1, 10))

        val area = WorkArea.of(pasture.blockPos, WorkEngineSettings.current, PastureAdapter.maxWanderDistance())
        val farOutside = helper.absolutePos(BlockPos(20, 2, 20))
        helper.assertFalse(area.contains(farOutside), "farOutside $farOutside unexpectedly lies inside $area")

        var injectedTick = -1L
        var checkedSlackTicks = 0

        helper.onEachTick {
            val worker = TestKit.workerOf(helper, charmander) ?: return@onEachTick
            if (worker.status == WorkerStatus.SLACKING) {
                if (injectedTick < 0L) {
                    injectedTick = helper.tick
                    charmander.brain.setMemory(MemoryModuleType.WALK_TARGET, WalkTarget(farOutside, 1.0f, 0))
                } else if (helper.tick > injectedTick) {
                    checkedSlackTicks++
                    helper.assertTrue(
                        area.contains(charmander.blockPosition()),
                        "CD-PRG-16: slacking worker walked outside workArea: ${charmander.blockPosition()} not in $area",
                    )
                    val walkGoal = charmander.brain.getMemory(MemoryModuleType.WALK_TARGET).orElse(null)?.target?.currentBlockPosition()
                    if (walkGoal != null) {
                        helper.assertTrue(
                            area.contains(walkGoal),
                            "CD-PRG-16: slacking WALK_TARGET $walkGoal remained outside workArea $area",
                        )
                    }
                    if (checkedSlackTicks == 10) {
                        charmander.brain.setMemory(MemoryModuleType.WALK_TARGET, WalkTarget(farOutside, 1.0f, 0))
                    }
                }
            } else if (injectedTick >= 0L && worker.status == WorkerStatus.WORKING) {
                helper.assertTrue(checkedSlackTicks >= 20, "checked only $checkedSlackTicks SLACKING ticks")
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

    @BeforeBatch(batch = TIGHT_SLACK_BATCH)
    fun enableTightWorkAreaSlack(@Suppress("UNUSED_PARAMETER") level: ServerLevel) {
        val current = WorkEngineSettings.current
        savedTightSettings = current
        WorkEngineSettings.current = current.copy(
            horizontalRadius = 4,
            verticalRadius = 2,
            baseSlackChance = 1.0,
            slackCheckTicks = 100,
            slackDurationTicks = 45,
        )
    }

    @AfterBatch(batch = TIGHT_SLACK_BATCH)
    fun restoreTightWorkAreaSlack(@Suppress("UNUSED_PARAMETER") level: ServerLevel) {
        WorkEngineSettings.current = savedTightSettings ?: EngineSettings.DEFAULT.copy(baseSlackChance = 0.0)
        savedTightSettings = null
    }

    private companion object {
        const val SLACK_BATCH = "progression_slack"
        const val TIGHT_SLACK_BATCH = "progression_tight_slack"
    }
}
