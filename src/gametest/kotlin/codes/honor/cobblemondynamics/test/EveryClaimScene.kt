package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.bed.BedTier
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.claim.TargetKey
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.leash.Binding
import codes.honor.cobblemondynamics.pasture.PastureKey
import codes.honor.cobblemondynamics.registry.DynamicsBlocks
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.UUID

/**
 * One Work Mode pasture whose workers hold every kind of claim at once. An auto-assigned Charmander works one furnace, and a Vulpix
 * that the Work Leash bound to a second furnace works that one: two work claims and a binding. A Tired Machop sleeps on a Hay Bed: a
 * bed claim. A Clefairy walks to the Machop to heal it: a heal claim. Until every claim is held, the scene keeps the Clefairy away
 * from the Machop, so the heal cannot end first.
 */
class EveryClaimScene(private val helper: GameTestHelper) {
    val owner: ServerPlayer = TestKit.owner(helper)
    val pasture: PokemonPastureBlockEntity = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
    val furnace: BlockPos
    val boundFurnace: BlockPos
    val bed: BlockPos = helper.absolutePos(BED_REL)
    val stoker: PokemonEntity
    val vulpix: PokemonEntity
    val sleeper: PokemonEntity
    val healer: PokemonEntity
    val binding: Binding
    val pastureKey: PastureKey

    private var tired = false

    init {
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        furnace = TestKit.furnace(helper, BlockPos(3, 1, 3), ItemStack(Items.RAW_IRON, 64)).blockPos
        val boundRel = BlockPos(11, 1, 3)
        boundFurnace = TestKit.furnace(helper, boundRel, ItemStack(Items.RAW_IRON, 64)).blockPos
        helper.setBlock(BED_REL, DynamicsBlocks.BEDS.getValue(BedTier.HAY).get())
        stoker = TestKit.tether(helper, pasture, owner, "charmander level=10")
        vulpix = TestKit.tether(helper, pasture, owner, "vulpix level=10")
        binding = TestKit.bind(helper, owner, vulpix, boundRel) ?: throw GameTestAssertException("the Vulpix did not bind")
        sleeper = TestKit.tether(helper, pasture, owner, "machop level=10")
        healer = TestKit.tether(helper, pasture, owner, "clefairy level=50")
        pastureKey = PastureKey(helper.level.dimension().location(), pasture.blockPos.asLong())
    }

    val workers: List<PokemonEntity> get() = listOf(stoker, vulpix, sleeper, healer)

    val ids: List<UUID> get() = workers.map { it.pokemon.uuid }

    /** Moves the scene on by one tick. True on each tick that all four claims are held, with the Clefairy on its way. */
    fun ready(): Boolean {
        val engine = TestKit.engine(helper)
        val sleeperWorker = TestKit.workerOf(helper, sleeper) ?: return false
        if (!tired) {
            TestKit.moveTo(helper, sleeper, BED_REL.south())
            sleeperWorker.stamina.fraction = 0.0
            sleeperWorker.tired = true
            tired = true
            return false
        }
        val claims = engine.claims
        val working = isWorkingOn(stoker, furnace) && isWorkingOn(vulpix, boundFurnace)
        val bedClaim = claims.claimOf(sleeper.pokemon.uuid)
        val asleep = sleeperWorker.status == WorkerStatus.SLEEPING && bedClaim?.kind == ClaimKind.BED &&
            bedClaim.key == TestKit.keyOf(helper, bed)
        val healClaim = claims.claimOf(healer.pokemon.uuid)
        val healing = TestKit.workerOf(helper, healer)?.status == WorkerStatus.HEALING && healClaim?.kind == ClaimKind.HEAL &&
            healClaim.key == TargetKey.Entity(sleeper.pokemon.uuid)
        if (working && asleep && healing) return true
        TestKit.moveTo(helper, healer, HEALER_HOLD)
        return false
    }

    /** What the scene waits for, for a test's timeout message. */
    fun describe(): String = workers.joinToString { entity ->
        "${entity.pokemon.species.name} ${TestKit.workerOf(helper, entity)?.status} ${TestKit.claimOf(helper, entity)}"
    }

    private fun isWorkingOn(entity: PokemonEntity, target: BlockPos): Boolean {
        val claim = TestKit.claimOf(helper, entity)
        return claim?.kind == ClaimKind.WORK &&
            claim.key == TestKit.keyOf(helper, target) &&
            TestKit.workerOf(helper, entity)?.status == WorkerStatus.WORKING
    }

    private companion object {
        val BED_REL = BlockPos(2, 2, 11)

        /** 10 blocks from the bed, inside the pasture's roam box. */
        val HEALER_HOLD = BlockPos(12, 2, 12)
    }
}
