package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.claim.TargetKey
import codes.honor.cobblemondynamics.config.EngineSettings
import codes.honor.cobblemondynamics.role.RoleDefinition
import codes.honor.cobblemondynamics.role.RoleSelection
import net.minecraft.world.phys.Vec3
import java.util.UUID

data class WorkerView(
    val id: UUID,
    val pos: Vec3,
    /** Primary first, which picks between the roles of one handler (ruling 1 of the Phase 2 plan). */
    val types: Set<String>,
    val hasFairyType: Boolean,
    val tired: Boolean,
    val staminaFraction: Double = 1.0,
    val allyNeedsHeal: Boolean = false,
    val settings: EngineSettings = EngineSettings.DEFAULT,
    val isSkipped: (TargetKey) -> Boolean,
)

/**
 * [needsDemand]: every role of the candidate releases its claim when done, so an idle worker takes it only with demand (CD-CRP-10).
 * [demandOf] tells whether one role of the candidate has demand there, which the pick asks only of a worker that fits two roles or more
 * of the candidate (CD-ASG-16).
 */
data class CandidateView(
    val key: TargetKey,
    val center: Vec3,
    val roles: List<RoleDefinition>,
    val needsDemand: Boolean = false,
    val demandOf: (RoleDefinition) -> Boolean = { true },
    val mayClaimRole: (RoleDefinition) -> Boolean = { true },
    val hasDemand: () -> Boolean,
)

/** Auto-assignment: which target and role a worker that a scan finds free gets. */
object Assigner {
    /**
     * CD-ASG-03: the nearest free target of an auto-assign role the worker can do. A role that releases its claim when done takes only
     * a target with demand, or the worker would claim a fully grown crop again on each scan pass (CD-CRP-10).
     */
    fun pickForIdle(w: WorkerView, candidates: List<CandidateView>, isFree: (TargetKey) -> Boolean): Pair<CandidateView, RoleDefinition>? {
        val autoTypes = autoTypesOf(w)
        if (autoTypes.isEmpty()) return null
        return options(w, autoTypes, candidates, isFree).firstOrNull { (candidate, _) -> !candidate.needsDemand || candidate.hasDemand() }
    }

    /** CD-ASG-19: the nearest free target with demand, for an auto worker that waited in Standby long enough. */
    fun pickForStandbyMove(
        w: WorkerView,
        current: TargetKey,
        candidates: List<CandidateView>,
        isFree: (TargetKey) -> Boolean,
    ): Pair<CandidateView, RoleDefinition>? {
        val autoTypes = autoTypesOf(w)
        if (autoTypes.isEmpty()) return null
        return options(
            w,
            autoTypes,
            candidates.filter {
                it.key != current
            },
            isFree,
        ).firstOrNull { (candidate, _) -> candidate.hasDemand() }
    }

    private fun autoTypesOf(w: WorkerView): Set<String> = DualFairyRule.autoAssignTypes(
        types = w.types,
        hasFairyType = w.hasFairyType,
        leashed = false,
        tired = w.tired,
        staminaFraction = w.staminaFraction,
        allyNeedsHeal = w.allyNeedsHeal,
        settings = w.settings,
    )

    /** The free targets nearest first, each with its role. The role of a candidate is asked only when the walk reaches it. */
    private fun options(
        w: WorkerView,
        autoTypes: Set<String>,
        candidates: List<CandidateView>,
        isFree: (TargetKey) -> Boolean,
    ): Sequence<Pair<CandidateView, RoleDefinition>> = candidates.asSequence()
        .filter { isFree(it.key) && !w.isSkipped(it.key) }
        .sortedWith(nearestTo(w))
        .mapNotNull { candidate -> roleFor(autoTypes, candidate)?.let { candidate to it } }

    /** CD-ASG-05, CD-ASG-22: a role whose auto-assign flag is false never counts. CD-ASG-16: a role with demand comes first. */
    private fun roleFor(autoTypes: Set<String>, candidate: CandidateView): RoleDefinition? = RoleSelection.withWork(
        candidate.roles.filter { it.autoAssign && RoleSelection.isEligible(it, autoTypes) && candidate.mayClaimRole(it) },
        autoTypes.toList(),
        hasDemand = candidate.demandOf,
    )

    /** Nearest first. Ties go to the lowest key, so the choice is stable. */
    private fun nearestTo(w: WorkerView): Comparator<CandidateView> =
        compareBy<CandidateView> { it.center.distanceToSqr(w.pos) }.thenBy(KEY_ORDER) { it.key }

    /** Block keys by `BlockPos.asLong()`, before entity keys by UUID. */
    private val KEY_ORDER: Comparator<TargetKey> = Comparator { a, b ->
        when {
            a is TargetKey.Block && b is TargetKey.Block -> a.pos.compareTo(b.pos).takeIf { it != 0 } ?: compareValues(a.lane, b.lane)
            a is TargetKey.Entity && b is TargetKey.Entity -> a.uuid.compareTo(b.uuid)
            a is TargetKey.Block -> -1
            else -> 1
        }
    }
}
