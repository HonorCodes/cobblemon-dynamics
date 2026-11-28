package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.leash.BindingStore
import codes.honor.cobblemondynamics.leash.LeashComponents
import codes.honor.cobblemondynamics.leash.LeashInteractions
import codes.honor.cobblemondynamics.leash.LeashResets
import codes.honor.cobblemondynamics.net.LeashOverviewPayload
import codes.honor.cobblemondynamics.pasture.PastureAdapter
import codes.honor.cobblemondynamics.pasture.WorkModePolicy.Decision
import codes.honor.cobblemondynamics.pasture.WorkModeService
import codes.honor.cobblemondynamics.test.create.CreateKit
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.storage.BottomlessStore
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.core.BlockPos
import net.minecraft.core.RegistryAccess
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import java.util.UUID

/** Shared pasture access, offline pastured ownership, controller bindings, and dual-type Fairy auto work (CD-TEAM-01 to CD-TEAM-10). */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class SharedPastureTests {
    private fun isWorkingAt(helper: GameTestHelper, entity: PokemonEntity, pos: BlockPos): Boolean {
        val claim = TestKit.claimOf(helper, entity)
        return claim?.kind == ClaimKind.WORK &&
            claim.key == TestKit.keyOf(helper, pos) &&
            TestKit.workerOf(helper, entity)?.status == WorkerStatus.WORKING
    }

    /**
     * CD-TEAM-01: while a pasture is unlocked, non-placer contributor B with a tethered Cobblemon toggles Work Mode, binds workers,
     * flips a water wheel, and receives the pasture's bound targets in LeashOverviewPayload, including on an ownerless pasture.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdTeam01UnlockedPastureContributorControlsAndSeesOutlines(helper: GameTestHelper) {
        val a = TestKit.owner(helper, "placer_a")
        val b = TestKit.owner(helper, "contrib_b")
        val c = TestKit.owner(helper, "stranger_c")
        val d = TestKit.owner(helper, "contrib_d")
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 9), a)
        TestKit.standBeside(a, pasture)
        TestKit.standBeside(b, pasture)
        TestKit.standBeside(c, pasture)
        val charmander = TestKit.tether(helper, pasture, b, "charmander level=10")
        val squirtle = TestKit.tether(helper, pasture, a, "squirtle level=10")

        helper.assertTrue(WorkModeService.request(c, pasture.blockPos, true) is Decision.Reject, "stranger C toggled Work Mode")
        helper.assertTrue(WorkModeService.request(b, pasture.blockPos, true) == Decision.Apply, "contributor B could not toggle Work Mode")
        helper.assertTrue(PastureAdapter.isWorkMode(pasture), "Work Mode stayed off after contributor B toggled it")

        val ownerless = TestKit.placePasture(helper, BlockPos(2, 1, 12), a)
        ownerless.ownerId = null
        TestKit.standBeside(d, ownerless)
        TestKit.tether(helper, ownerless, d, "rattata level=5")
        helper.assertTrue(WorkModeService.requestLock(d, ownerless.blockPos, true) is Decision.Reject, "non-op locked ownerless pasture")
        helper.assertTrue(WorkModeService.request(d, ownerless.blockPos, true) == Decision.Apply, "contributor could not toggle ownerless")

        val furnaceRel = BlockPos(4, 1, 5)
        val wheelRel = BlockPos(10, 2, 5)
        val furnace = TestKit.furnace(helper, furnaceRel, ItemStack(Items.RAW_IRON, 16)).blockPos
        val wheel = CreateKit.smallWheel(helper, wheelRel)
        helper.assertTrue(TestKit.bind(helper, b, charmander, furnaceRel)?.target == furnace, "contributor B did not bind furnace")
        helper.assertTrue(TestKit.bind(helper, b, squirtle, wheelRel)?.target == wheel.blockPos, "contributor B did not bind wheel")

        val bTargets = LeashOverviewPayload.of(b).entries.map { it.second }.toSet()
        helper.assertTrue(bTargets == setOf(furnace, wheel.blockPos), "CD-TEAM-01: B's overview holds $bTargets")
        helper.assertTrue(LeashOverviewPayload.of(c).entries.isEmpty(), "CD-UI-09: stranger C's overview is not empty")

        helper.onEachTick {
            if (!isWorkingAt(helper, squirtle, wheel.blockPos)) return@onEachTick
            helper.assertTrue(LeashInteractions.onSneakBlockClick(b, wheel.blockPos), "CD-TEAM-01: contributor B could not flip wheel")
            pasture.releasePokemon(charmander.pokemon.uuid)
            helper.assertFalse(
                LeashInteractions.onSneakBlockClick(b, wheel.blockPos),
                "revoked former binder B flipped wheel after untethering all Cobblemon",
            )
            helper.assertTrue(LeashInteractions.onSneakBlockClick(a, wheel.blockPos), "placer A could not flip wheel")
            helper.succeed()
        }
    }

    /**
     * CD-TEAM-02, CD-TEAM-03: Access Lock persists across save and reload (both when Work Mode is on and when it is off), blocks
     * non-teammate contributor B from toggling Work Mode or selecting workers, and still lets B sneak-unbind B's own Cobblemon.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 200)
    fun cdTeam02Team03LockedPasturePersistsAndPreservesOwnerUnbind(helper: GameTestHelper) {
        val a = TestKit.owner(helper, "placer_a")
        val b = TestKit.owner(helper, "contrib_b")
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), a)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(a, pasture)
        TestKit.standBeside(b, pasture)
        val cauldronRel = BlockPos(7, 1, 3)
        helper.setBlock(cauldronRel, Blocks.CAULDRON)
        val cauldron = helper.absolutePos(cauldronRel)
        val vaporeon = TestKit.tether(helper, pasture, b, "vaporeon level=30")
        helper.assertTrue(TestKit.bind(helper, b, vaporeon, cauldronRel)?.target == cauldron, "B did not bind Vaporeon while unlocked")

        helper.assertTrue(WorkModeService.requestLock(b, pasture.blockPos, true) is Decision.Reject, "non-teammate B locked the pasture")
        helper.assertTrue(WorkModeService.requestLock(a, pasture.blockPos, true) == Decision.Apply, "placer A could not lock the pasture")
        helper.assertTrue(PastureAdapter.isLocked(pasture), "pasture is not locked")

        val access = helper.level.registryAccess()
        val reloadedOn = BlockEntity.loadStatic(pasture.blockPos, pasture.blockState, pasture.saveWithFullMetadata(access), access)
            as PokemonPastureBlockEntity
        helper.assertTrue(PastureAdapter.isWorkMode(reloadedOn) && PastureAdapter.isLocked(reloadedOn), "CD-TEAM-03: lock lost on reload")

        val offLocked = TestKit.placePasture(helper, BlockPos(2, 1, 2), a)
        WorkModeService.applyLock(helper.level, offLocked.blockPos, true)
        val reloadedOff = BlockEntity.loadStatic(offLocked.blockPos, offLocked.blockState, offLocked.saveWithFullMetadata(access), access)
            as PokemonPastureBlockEntity
        helper.assertTrue(!PastureAdapter.isWorkMode(reloadedOff) && PastureAdapter.isLocked(reloadedOff), "lock lost with Work Mode off")

        helper.assertTrue(WorkModeService.request(b, pasture.blockPos, false) is Decision.Reject, "CD-TEAM-02: B toggled locked pasture")
        val stack = TestKit.leash(b)
        TestKit.clickEntity(b, vaporeon)
        helper.assertTrue(stack.get(LeashComponents.SELECTION.get()) == null, "CD-TEAM-02: B selected on a locked pasture")

        TestKit.clickEntity(b, vaporeon, sneak = true)
        helper.runAfterDelay(1) {
            helper.assertTrue(BindingStore.read(vaporeon.pokemon) == null, "CD-TEAM-02: B could not sneak-unbind own Vaporeon")
            helper.assertTrue(TestKit.reservationAt(helper, cauldron) == null, "CD-TEAM-02: cauldron stayed reserved after sneak-unbind")
            helper.succeed()
        }
    }

    /**
     * CD-TEAM-04: a pastured worker whose owner is offline and whose entity holds a deserialized unlinked Pokemon copy
     * (`entity.pokemon.getOwnerUUID() == null`) still binds, enters Working, survives a restart, and keeps its binding across ticks.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_000, batch = "restart_team04")
    fun cdTeam04OfflineOwnerWorkerKeepsBindingAndWorks(helper: GameTestHelper) {
        val offlineOwner = TestKit.owner(helper, "offline_a")
        helper.assertTrue(helper.level.server.playerList.getPlayer(offlineOwner.uuid) == null, "owner is online in PlayerList")
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), offlineOwner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(offlineOwner, pasture)
        val furnaceRel = BlockPos(7, 1, 3)
        val furnace = TestKit.furnace(helper, furnaceRel, ItemStack(Items.RAW_IRON, 64)).blockPos
        val charmander = TestKit.tether(helper, pasture, offlineOwner, "charmander level=10")
        val pcStore = Cobblemon.storage.getPC(offlineOwner)
        val pcPokemon = pcStore[charmander.pokemon.uuid] ?: error("missing Charmander in PC")

        val access = helper.level.registryAccess()
        charmander.setPersistenceRequired()
        charmander.pokemon = unlinkedCopy(helper, access, pcPokemon)

        TestKit.leash(offlineOwner)
        TestKit.clickEntity(offlineOwner, charmander)
        TestKit.clickBlock(helper, offlineOwner, furnaceRel)
        val bound = BindingStore.read(pcPokemon)
        helper.assertTrue(bound?.target == furnace && bound.owner == pcStore.uuid, "offline owner unlinked Charmander bind gave $bound")

        var restarted = false
        var workingSince = -1L
        helper.onEachTick {
            helper.assertFalse(charmander.isRemoved, "CD-TEAM-04: Charmander removed at tick ${helper.tick}")
            helper.assertTrue(BindingStore.read(pcPokemon) == bound, "CD-TEAM-04: binding lost at tick ${helper.tick}")
            helper.assertTrue(
                charmander.pokemon.getOwnerUUID() == null,
                "CD-TEAM-04: entity.pokemon gained an owner at tick ${helper.tick}",
            )
            if (!restarted) {
                if (!isWorkingAt(helper, charmander, furnace)) return@onEachTick
                charmander.pokemon = unlinkedCopy(helper, access, pcPokemon)
                TestKit.simulateRestart(helper)
                restarted = true
                return@onEachTick
            }
            if (isWorkingAt(helper, charmander, furnace)) {
                if (workingSince < 0) workingSince = helper.tick
                if (helper.tick - workingSince >= 100) helper.succeed()
            }
        }
    }

    private fun unlinkedCopy(helper: GameTestHelper, access: RegistryAccess, pcPokemon: Pokemon): Pokemon {
        val copy = Pokemon.loadFromNBT(access, pcPokemon.saveToNBT(access))
        copy.tetheringId = null
        BottomlessStore(UUID.randomUUID()).add(copy)
        copy.tetheringId = pcPokemon.tetheringId
        helper.assertTrue(
            copy.getOwnerUUID() == null && !copy.isPlayerOwned() && copy.tetheringId == pcPokemon.tetheringId,
            "unlinked copy state invalid",
        )
        return copy
    }

    /**
     * CD-TEAM-05, CD-TEAM-06: authorized controller B selects, binds, and sneak-unbinds player A's pastured Charmander; Ref.owner stores
     * A's PC id while Binding.binder stores B's UUID, LeashResets.onGained keeps the binding for A, and strangers cannot select or clear.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 200)
    fun cdTeam05Team06ControllerSelectsBindsAndUnbindsTeammatePokemon(helper: GameTestHelper) {
        val a = TestKit.owner(helper, "owner_a")
        val b = TestKit.owner(helper, "controller_b")
        val c = TestKit.owner(helper, "stranger_c")
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), a)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(a, pasture)
        TestKit.standBeside(b, pasture)
        TestKit.standBeside(c, pasture)
        val charmander = TestKit.tether(helper, pasture, a, "charmander level=10")
        TestKit.tether(helper, pasture, b, "rattata level=5")
        val furnaceRel = BlockPos(7, 1, 3)
        val furnace = TestKit.furnace(helper, furnaceRel, ItemStack(Items.RAW_IRON, 16)).blockPos

        val cStack = TestKit.leash(c)
        TestKit.clickEntity(c, charmander)
        helper.assertTrue(cStack.get(LeashComponents.SELECTION.get()) == null, "CD-SEC-07: stranger C selected A's Charmander")

        val binding = TestKit.bind(helper, b, charmander, furnaceRel)
        val aPcId = Cobblemon.storage.getPC(a).uuid
        helper.assertTrue(
            binding?.target == furnace && binding.binder == b.uuid && binding.owner == aPcId,
            "CD-TEAM-06: B's bind produced $binding",
        )
        val ref = TestKit.reservationAt(helper, furnace)
        helper.assertTrue(ref?.owner == aPcId && ref.owner != b.uuid, "CD-TEAM-05: Ref.owner is ${ref?.owner}, expected A's pcId $aPcId")

        LeashResets.unindex(helper.level, furnace, charmander.pokemon.uuid)
        LeashResets.onGained(helper.level.server, a.uuid, charmander.pokemon)
        helper.assertTrue(
            BindingStore.read(charmander.pokemon) == binding,
            "CD-TEAM-05: onGained wiped teammate-placed binding for owner A without LEASH_INDEX",
        )
        LeashResets.onGained(helper.level.server, b.uuid, charmander.pokemon)
        helper.assertTrue(
            BindingStore.read(charmander.pokemon) == null,
            "CD-TEAM-05: onGained failed to clear binding when Charmander was traded to binder B without LEASH_INDEX",
        )
        val rebound = TestKit.bind(helper, b, charmander, furnaceRel)
        helper.assertTrue(rebound != null, "CD-TEAM-06: B could not rebind Charmander")

        helper.assertTrue(furnace in LeashOverviewPayload.of(a).entries.map { it.second }, "owner A missing furnace in overview")
        helper.assertTrue(furnace in LeashOverviewPayload.of(b).entries.map { it.second }, "controller B missing furnace in overview")
        helper.assertTrue(LeashOverviewPayload.of(c).entries.isEmpty(), "stranger C received shared pasture overview")

        TestKit.clickEntity(c, charmander, sneak = true)
        helper.assertTrue(BindingStore.read(charmander.pokemon) == rebound, "stranger C cleared A's Charmander binding")

        TestKit.leash(b)
        TestKit.clickEntity(b, charmander, sneak = true)
        helper.runAfterDelay(1) {
            helper.assertTrue(
                BindingStore.read(charmander.pokemon) == null,
                "CD-TEAM-06: controller B could not sneak-unbind A's Charmander",
            )
            helper.assertTrue(TestKit.reservationAt(helper, furnace) == null, "CD-TEAM-06: furnace stayed reserved after B's sneak-unbind")
            helper.succeed()
        }
    }

    /**
     * CD-TEAM-09, CD-TEAM-10: an unbound Azumarill (Water and Fairy) beside a dry water wheel with a full-stamina Machop ally claims and
     * works the wheel, and when Machop becomes Tired on a later tick, Azumarill preempts its wheel claim and heals Machop to 100%.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 1_000)
    fun cdTeam09Team10UnboundDualFairyWorksWheelAndPreemptsForHeal(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 9), owner)
        TestKit.workMode(pasture, true)
        val wheel = CreateKit.smallWheel(helper, BlockPos(7, 2, 5))
        val azumarill = TestKit.tether(helper, pasture, owner, "azumarill level=50")
        val machop = TestKit.tether(helper, pasture, owner, "machop level=10")
        TestKit.moveTo(helper, machop, BlockPos(4, 2, 9))
        var hurtAt = -1L
        var beforeHealStamina = -1.0
        helper.onEachTick {
            val azuWorker = TestKit.workerOf(helper, azumarill) ?: return@onEachTick
            val machopWorker = TestKit.workerOf(helper, machop) ?: return@onEachTick
            if (hurtAt < 0) {
                if (!isWorkingAt(helper, azumarill, wheel.blockPos)) return@onEachTick
                beforeHealStamina = azuWorker.stamina.fraction
                machopWorker.stamina.fraction = 0.0
                machopWorker.tired = true
                hurtAt = helper.tick
                return@onEachTick
            }
            if (machopWorker.tired) {
                helper.assertTrue(helper.tick - hurtAt < 500, "CD-TEAM-10: Azumarill did not preempt and heal Machop within 500 ticks")
                return@onEachTick
            }
            helper.assertTrue(machopWorker.stamina.percent == 100.0, "CD-TEAM-10: healed Machop reads ${machopWorker.stamina.percent}%")
            helper.assertTrue(beforeHealStamina - azuWorker.stamina.fraction >= 0.20, "CD-TEAM-10: Azumarill paid no heal stamina")
            helper.succeed()
        }
    }
}
