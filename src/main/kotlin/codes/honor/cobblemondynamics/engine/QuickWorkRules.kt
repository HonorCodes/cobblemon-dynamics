package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.handler.BindingShape
import codes.honor.cobblemondynamics.role.RoleDefinition
import codes.honor.cobblemondynamics.role.RoleSelection

/** The pure rules of a Quick Work order (ADR-0013). The engine reads every input from server state. */
object QuickWorkRules {
    const val STARTED = "cobblemon_dynamics.quick_work.started"
    const val NEEDS_LEASH = "cobblemon_dynamics.quick_work.needs_leash"
    const val NO_PERMISSION = "cobblemon_dynamics.quick_work.no_permission"
    const val TIRED = "cobblemon_dynamics.quick_work.tired"
    const val CLAIMED = "cobblemon_dynamics.quick_work.claimed"
    const val NO_DEMAND = "cobblemon_dynamics.quick_work.no_demand"

    /** R57's text, which the leash sends already. Every Quick Work key takes the Pokémon's name, then the target's name. */
    const val CANNOT_REACH = "cobblemon_dynamics.leash.cannot_reach"

    /** An aim waits this long for the end of its send-out, which Cobblemon posts 30 ticks after the key press. */
    const val AIM_TICKS = 100L

    /** Cobblemon's `BEAM_MODE` while a recall plays (`PokemonEntity.recallWithAnimation`). */
    const val RECALL_BEAM = 3

    sealed interface Pick {
        data class Order(val role: RoleDefinition) : Pick

        /** Only roles that need the Work Leash fit the block (CD-QWK-04). */
        data object NeedsLeash : Pick

        /** No role fits, or the Pokémon is a healer. The send-out stays a normal one, with no message. */
        data object None : Pick
    }

    /** What one tick of an order sees, before its worker's own tick. */
    data class Watch(
        /** Removed, dead, in Cobblemon's recall beam, or no longer the Pokémon's sent-out entity. */
        val entityGone: Boolean,
        val battling: Boolean,
        /** Online and in the order's level. */
        val ownerPresent: Boolean,
        /** From the owner's eye position to the box of the aimed block. */
        val ownerDistanceSqr: Double,
        val leashRange: Int,
        val holdsClaim: Boolean,
        val status: WorkerStatus,
        val ticksInStatus: Long,
        val standbyLimit: Int,
    )

    /** CD-CFG-16, CD-CFG-18, CD-QWK-21: how far the trace of a send-out reaches, or null for no trace. */
    fun traceRange(enabled: Boolean, ownerBattling: Boolean, targetRange: Int, leashRange: Int): Int? =
        if (enabled && !ownerBattling) minOf(targetRange, leashRange) else null

    /** CD-QWK-03: a role that an auto claim takes, from a pasture scan, on one target. */
    fun mayOrder(role: RoleDefinition, scanned: Boolean, shape: BindingShape): Boolean =
        role.autoAssign && scanned && shape == BindingShape.SINGLE

    /**
     * CD-QWK-03 to CD-QWK-05. [fitting] are the roles that the Pokémon's [types], primary first, make it eligible for and whose
     * handler matches the aimed block. [orderable] is [mayOrder] of each. A Fairy-type is a healer, which no auto claim gives a
     * machine (CD-ASG-04).
     */
    fun pick(fitting: List<RoleDefinition>, types: List<String>, hasFairyType: Boolean, orderable: (RoleDefinition) -> Boolean): Pick {
        if (hasFairyType || fitting.isEmpty()) return Pick.None
        val role = RoleSelection.best(fitting.filter(orderable), types) ?: return Pick.NeedsLeash
        return Pick.Order(role)
    }

    /** CD-QWK-06 to CD-QWK-09: the first refusal of an order, in this order, or null to try the claim. [hasDemand] runs last. */
    fun refusal(mayUse: Boolean, tired: Boolean, targetFree: Boolean, hasDemand: () -> Boolean): String? = when {
        !mayUse -> NO_PERMISSION
        tired -> TIRED
        !targetFree -> CLAIMED
        !hasDemand() -> NO_DEMAND
        else -> null
    }

    /** CD-QWK-13, CD-QWK-16, CD-QWK-18 to CD-QWK-21: the first reason that ends an order now, or null. */
    fun endReason(w: Watch): QuickEnd? = when {
        w.entityGone -> QuickEnd.RECALLED
        w.battling -> QuickEnd.BATTLE
        !w.ownerPresent -> QuickEnd.OWNER_LEFT
        w.ownerDistanceSqr > w.leashRange.toDouble() * w.leashRange -> QuickEnd.TOO_FAR
        !w.holdsClaim -> QuickEnd.TARGET
        w.status == WorkerStatus.STANDBY && w.ticksInStatus >= w.standbyLimit -> QuickEnd.NO_DEMAND
        else -> null
    }
}

/** Why an order ended, with the lang key of the owner's message. */
enum class QuickEnd(val messageKey: String) {
    TOO_FAR("cobblemon_dynamics.quick_work.end.too_far"),
    RECALLED("cobblemon_dynamics.quick_work.end.recalled"),
    TIRED("cobblemon_dynamics.quick_work.end.tired"),
    TARGET("cobblemon_dynamics.quick_work.end.target"),
    NO_DEMAND("cobblemon_dynamics.quick_work.end.no_demand"),
    OWNER_LEFT("cobblemon_dynamics.quick_work.end.owner_left"),
    BATTLE("cobblemon_dynamics.quick_work.end.battle"),
    UNREACHABLE(QuickWorkRules.CANNOT_REACH),
}
