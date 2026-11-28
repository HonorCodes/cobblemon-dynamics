package codes.honor.cobblemondynamics.handler.cobblemon

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.block.entity.HealingMachineBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * The Generator on Cobblemon's Healing Machine: an Electric-type recharges the machine through its public charge, at a multiple of
 * Cobblemon's own recharge rate (CD-MCH-08, CD-BAL-16). The machine is no energy block, so the FE Generator never matches it.
 */
object HealingChargeHandler : TargetHandler {
    override val id: ResourceLocation = id("healing_charge")
    override val scanKind: ScanKind = ScanKind.BLOCK_ENTITY

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        blockEntity is HealingMachineBlockEntity

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = level.getBlockEntity(canonical) is HealingMachineBlockEntity

    /** CD-MCH-08: while the machine holds less than its maximum charge and recharges itself. */
    override fun hasDemand(ctx: WorkContext): Boolean = machineAt(ctx)?.let { added(ctx, it) > 0f } ?: false

    override fun applyWork(ctx: WorkContext) {
        val machine = machineAt(ctx) ?: return
        val added = added(ctx, machine)
        if (added <= 0f) return
        machine.healingCharge += added
        machine.setChanged()
    }

    private fun added(ctx: WorkContext, machine: HealingMachineBlockEntity): Float {
        val config = Cobblemon.config
        val own = HealingCharge.ownPerTick(machine.maxCharge, config.secondsToChargeHealingMachine)
        val perTick = HealingCharge.workerPerTick(
            HealingParams.of(ctx.role.params).multiplierAt100,
            ctx.pokemon.level,
            own,
            ctx.multiplier,
        )
        val recharges = !config.infiniteHealerCharge && !machine.infinite && !machine.isInUse
        return HealingCharge.added(machine.healingCharge, machine.maxCharge, own, perTick, recharges)
    }

    private fun machineAt(ctx: WorkContext): HealingMachineBlockEntity? = ctx.level.getBlockEntity(ctx.target) as? HealingMachineBlockEntity
}
