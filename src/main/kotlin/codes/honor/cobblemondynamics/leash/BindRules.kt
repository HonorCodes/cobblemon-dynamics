package codes.honor.cobblemondynamics.leash

import codes.honor.cobblemondynamics.handler.BindingShape
import codes.honor.cobblemondynamics.role.RoleDefinition
import codes.honor.cobblemondynamics.role.RoleSelection
import net.minecraft.core.BlockPos

/** Whether a Work Leash block click binds, waits for a destination, or is refused. The caller reads every input from server state. */
object BindRules {
    const val NO_SELECTION = "cobblemon_dynamics.leash.no_selection"
    const val NOT_WORK_MODE = "cobblemon_dynamics.leash.not_work_mode"
    const val NO_ROLE = "cobblemon_dynamics.leash.no_role"
    const val OUTSIDE_AREA = "cobblemon_dynamics.leash.outside_area"
    const val ALREADY_ASSIGNED = "cobblemon_dynamics.leash.already_assigned"
    const val DESTINATION_SAME = "cobblemon_dynamics.leash.destination_same"

    sealed interface Outcome {
        data class Bind(val role: RoleDefinition, val canonical: BlockPos) : Outcome

        data class AwaitDestination(val role: RoleDefinition) : Outcome

        data class Refuse(val messageKey: String, val arg: String? = null) : Outcome
    }

    /**
     * For a click with a pending source, [fittingRoles], [insideWorkArea] and [boundToOther] describe the source, and
     * [canonical] is the destination.
     */
    data class Input(
        /** Read from the server's copy of the stack only (CD-SEC-08). */
        val selection: LeashSelection?,
        val pokemonOwnedByPlayer: Boolean,
        /** And the Pokémon is still tethered to that pasture. */
        val pastureInWorkMode: Boolean,
        /** The roles that the Pokémon's types make it eligible for, whose handler matches the clicked block. */
        val fittingRoles: List<RoleDefinition>,
        val canonical: BlockPos,
        val insideWorkArea: Boolean,
        /** The display name of another Pokémon that is bound to the target. */
        val boundToOther: String?,
        val pending: PendingSource?,
        val sameAsPending: Boolean,
    )

    fun evaluate(input: Input, shapeOf: (RoleDefinition) -> BindingShape): Outcome {
        if (input.selection == null || !input.pokemonOwnedByPlayer) return Outcome.Refuse(NO_SELECTION)
        if (!input.pastureInWorkMode) return Outcome.Refuse(NOT_WORK_MODE)
        // CD-LSH-16: one click gives one predictable role.
        val role = RoleSelection.best(input.fittingRoles) ?: return Outcome.Refuse(NO_ROLE)
        val twoTargets = shapeOf(role) == BindingShape.SOURCE_AND_DESTINATION
        if (twoTargets && input.pending != null && input.sameAsPending) return Outcome.Refuse(DESTINATION_SAME)
        // The source of a two-target role is checked at its own click, so a refusal never waits for the second click.
        if (!input.insideWorkArea) return Outcome.Refuse(OUTSIDE_AREA)
        input.boundToOther?.let { return Outcome.Refuse(ALREADY_ASSIGNED, it) }
        return if (twoTargets && input.pending == null) Outcome.AwaitDestination(role) else Outcome.Bind(role, input.canonical)
    }
}
