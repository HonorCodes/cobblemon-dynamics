package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.claim.TargetKey
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
    val isSkipped: (TargetKey) -> Boolean,
)

data class CandidateView(val key: TargetKey, val center: Vec3, val roles: List<RoleDefinition>, val hasDemand: () -> Boolean)

/** Auto-assignment: which target and role a worker that a scan finds free gets. */
object Assigner {
    /** CD-ASG-03: the nearest free target of an auto-assign role the worker can do. */
    fun pickForIdle(w: WorkerView, candidates: List<CandidateView>, isFree: (TargetKey) -> Boolean): Pair<CandidateView, RoleDefinition>? =
        if (!mayAutoAssign(w)) {
            null
        } else {
            options(w, candidates, isFree).minWithOrNull(nearestTo(w))
        }

    /** CD-ASG-19: the nearest free target with demand, for an auto worker that waited in Standby long enough. */
    fun pickForStandbyMove(
        w: WorkerView,
        current: TargetKey,
        candidates: List<CandidateView>,
        isFree: (TargetKey) -> Boolean,
    ): Pair<CandidateView, RoleDefinition>? = if (!mayAutoAssign(w)) {
        null
    } else {
        options(w, candidates.filter { it.key != current }, isFree)
            .filter { (candidate, _) -> candidate.hasDemand() }
            .minWithOrNull(nearestTo(w))
    }

    // CD-ASG-04: a Fairy type heals and never takes a machine through an auto claim. CD-STA-09: a Tired worker takes none.
    private fun mayAutoAssign(w: WorkerView): Boolean = !w.tired && !w.hasFairyType

    private fun options(
        w: WorkerView,
        candidates: List<CandidateView>,
        isFree: (TargetKey) -> Boolean,
    ): Sequence<Pair<CandidateView, RoleDefinition>> = candidates.asSequence()
        .filter { isFree(it.key) && !w.isSkipped(it.key) }
        .mapNotNull { candidate -> roleFor(w, candidate)?.let { candidate to it } }

    /** CD-ASG-05: a role whose auto-assign flag is false never counts. */
    private fun roleFor(w: WorkerView, candidate: CandidateView): RoleDefinition? =
        RoleSelection.best(candidate.roles.filter { it.autoAssign && RoleSelection.isEligible(it, w.types) }, w.types.toList())

    /** Nearest first. Ties go to the lowest key, so the choice is stable. */
    private fun nearestTo(w: WorkerView): Comparator<Pair<CandidateView, RoleDefinition>> =
        compareBy<Pair<CandidateView, RoleDefinition>> { it.first.center.distanceToSqr(w.pos) }.thenBy(KEY_ORDER) { it.first.key }

    /** Block keys by `BlockPos.asLong()`, before entity keys by UUID. */
    private val KEY_ORDER: Comparator<TargetKey> = Comparator { a, b ->
        when {
            a is TargetKey.Block && b is TargetKey.Block -> a.pos.compareTo(b.pos)
            a is TargetKey.Entity && b is TargetKey.Entity -> a.uuid.compareTo(b.uuid)
            a is TargetKey.Block -> -1
            else -> 1
        }
    }
}
