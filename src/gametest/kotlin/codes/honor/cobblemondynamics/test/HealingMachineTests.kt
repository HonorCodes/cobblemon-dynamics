package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.handler.cobblemon.HealingChargeHandler
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.role.RoleRegistry
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.block.HealingMachineBlock
import com.cobblemon.mod.common.block.entity.HealingMachineBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

/** The Generator at Cobblemon's real Healing Machine, whose own tick recharges it beside the worker. */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class HealingMachineTests {
    private val machineRel = BlockPos(7, 1, 3)

    /** The ticks over which the test measures the recharge rate. */
    private val sampleTicks = 100

    private fun isWorkingOn(helper: GameTestHelper, entity: PokemonEntity, machine: HealingMachineBlockEntity): Boolean {
        val claim = TestKit.claimOf(helper, entity)
        return claim?.kind == ClaimKind.WORK &&
            claim.key == TestKit.keyOf(helper, machine.blockPos) &&
            TestKit.workerOf(helper, entity)?.status == WorkerStatus.WORKING
    }

    /**
     * CD-MCH-08: a level 100 Pikachu recharges an empty Healing Machine at 4 times Cobblemon's own rate, on top of the machine's own
     * recharge, so 5 times in all. Near its maximum the machine's own tick makes the last step: the charge stops at the maximum, the
     * block shows full charge, and the worker waits in Standby.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 800)
    fun cdMch08HealingMachineRecharge(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        helper.setBlock(machineRel, CobblemonBlocks.HEALING_MACHINE)
        val machine = helper.level.getBlockEntity(helper.absolutePos(machineRel)) as? HealingMachineBlockEntity
            ?: throw GameTestAssertException("no Healing Machine block entity at $machineRel")
        machine.healingCharge = 0f
        val role = RoleRegistry.current.get(id("generator_healing_machine"))
        helper.assertTrue(role?.handler === HealingChargeHandler, "the Healing Machine role is not active with its handler: $role")
        val own = machine.maxCharge / (Cobblemon.config.secondsToChargeHealingMachine * 20)
        val pikachu = TestKit.tether(helper, pasture, owner, "pikachu level=100")
        var step = "claim"
        var since = -1L
        var start = 0f
        helper.onEachTick {
            val worker = TestKit.workerOf(helper, pikachu) ?: return@onEachTick
            helper.assertTrue(machine.healingCharge <= machine.maxCharge, "the charge ${machine.healingCharge} passed its maximum")
            when (step) {
                "claim" -> if (isWorkingOn(helper, pikachu, machine)) {
                    step = "sample"
                    since = helper.tick
                    start = machine.healingCharge
                }

                "sample" -> if (helper.tick - since == sampleTicks.toLong()) {
                    val gained = machine.healingCharge - start
                    val expected = sampleTicks * 5 * own
                    helper.assertTrue(
                        abs(gained - expected) <= 2 * 5 * own,
                        "the machine gained $gained in $sampleTicks ticks, not $expected",
                    )
                    machine.healingCharge = machine.maxCharge - 0.05f
                    step = "fill"
                    since = helper.tick
                }

                "fill" -> if (machine.healingCharge >= machine.maxCharge) {
                    val level = helper.level.getBlockState(machine.blockPos).getValue(HealingMachineBlock.CHARGE_LEVEL)
                    helper.assertTrue(level == HealingMachineBlock.MAX_CHARGE_LEVEL, "the full machine shows charge level $level")
                    step = "full"
                    since = helper.tick
                } else {
                    helper.assertTrue(helper.tick - since <= 60, "the machine is not full 60 ticks after 0.05 below its maximum")
                }

                "full" -> {
                    helper.assertTrue(machine.healingCharge == machine.maxCharge, "the full machine holds ${machine.healingCharge}")
                    if (worker.status == WorkerStatus.STANDBY) {
                        helper.succeed()
                    } else {
                        helper.assertTrue(helper.tick - since <= 40, "the Pikachu is ${worker.status} 40 ticks after the machine filled")
                    }
                }
            }
        }
    }
}
