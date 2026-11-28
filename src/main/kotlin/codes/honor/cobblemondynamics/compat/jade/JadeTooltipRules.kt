package codes.honor.cobblemondynamics.compat.jade

import codes.honor.cobblemondynamics.client.PastureShelfState
import codes.honor.cobblemondynamics.client.RoleNames
import codes.honor.cobblemondynamics.client.StaminaBar
import codes.honor.cobblemondynamics.client.TextSpec
import codes.honor.cobblemondynamics.client.WorkModeButton
import codes.honor.cobblemondynamics.client.WorkerTooltipLines
import codes.honor.cobblemondynamics.command.StaminaReport
import codes.honor.cobblemondynamics.engine.IdleReason
import codes.honor.cobblemondynamics.engine.WorkerStatus
import java.util.Locale

data class WorkedMachineEntry(val workerName: String, val rolePath: String, val status: WorkerStatus)

object JadeTooltipRules {
    const val WORKER_ROLE_STATE = "cobblemon_dynamics.jade.worker.role_state"
    const val WORKER_STATE = "cobblemon_dynamics.jade.worker.state"
    const val WORKER_MULTIPLIER = "cobblemon_dynamics.jade.worker.multiplier"
    const val MACHINE_WORKER = "cobblemon_dynamics.jade.machine.worker"
    const val PASTURE_ACTIVE = "cobblemon_dynamics.jade.pasture.active"
    const val BED_FACTOR = "cobblemon_dynamics.jade.bed.factor"
    const val BED_OCCUPIED = "cobblemon_dynamics.jade.bed.occupied"
    const val BED_FREE = "cobblemon_dynamics.jade.bed.free"

    fun formatMultiplier(multiplier: Double): String {
        val rounded = String.format(Locale.ROOT, "%.2f", multiplier)
        return rounded.trimEnd('0').trimEnd('.')
    }

    fun workerLines(
        percent: Int,
        status: WorkerStatus,
        idleReason: IdleReason,
        traitFlags: Int,
        rolePath: String? = null,
        multiplier: Double = 1.0,
    ): List<TextSpec> {
        val lines = ArrayList<TextSpec>(5)
        val stateSpec = TextSpec.Key(StaminaReport.stateKey(status))
        if (!rolePath.isNullOrBlank()) {
            lines += TextSpec.Key(WORKER_ROLE_STATE, listOf(RoleNames.specFor(rolePath), stateSpec))
        } else {
            lines += TextSpec.Key(WORKER_STATE, listOf(stateSpec))
        }
        lines += TextSpec.Key(StaminaBar.TOOLTIP, listOf(percent))
        val reasonKey = idleReason.takeIf { it != IdleReason.NONE }?.langKey
        if (reasonKey != null) {
            lines += TextSpec.Key(WorkerTooltipLines.IDLE_LINE, listOf(TextSpec.Key(reasonKey)))
        }
        WorkerTooltipLines.traitLine(traitFlags)?.let(lines::add)
        lines += TextSpec.Key(WORKER_MULTIPLIER, listOf(formatMultiplier(multiplier)))
        return lines
    }

    fun machineLines(workers: List<WorkedMachineEntry>): List<TextSpec> = workers.map { entry ->
        TextSpec.Key(
            MACHINE_WORKER,
            listOf(
                entry.workerName,
                RoleNames.specFor(entry.rolePath),
                TextSpec.Key(StaminaReport.stateKey(entry.status)),
            ),
        )
    }

    fun pastureLines(workMode: Boolean, radius: Int, locked: Boolean, workerCount: Int): List<TextSpec> {
        if (!workMode) return listOf(TextSpec.Key(WorkModeButton.OFF))
        val lines = ArrayList<TextSpec>(2)
        lines += TextSpec.Key(PASTURE_ACTIVE, listOf(workerCount, radius))
        if (locked) lines += TextSpec.Key(PastureShelfState.ACCESS_LOCKED)
        return lines
    }

    fun bedLines(factor: Int, sleeperName: String?): List<TextSpec> = listOf(
        TextSpec.Key(BED_FACTOR, listOf(factor)),
        if (!sleeperName.isNullOrBlank()) {
            TextSpec.Key(BED_OCCUPIED, listOf(sleeperName))
        } else {
            TextSpec.Key(BED_FREE)
        },
    )
}
