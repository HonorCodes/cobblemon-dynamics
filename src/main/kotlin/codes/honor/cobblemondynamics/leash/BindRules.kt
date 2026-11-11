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
    const val ALREADY_ASSIGNED_OTHER = "cobblemon_dynamics.leash.already_assigned_other"
    const val DESTINATION_SAME = "cobblemon_dynamics.leash.destination_same"
    const val TOO_FAR = "cobblemon_dynamics.leash.too_far"

    sealed interface Outcome {
        data class Bind(val role: RoleDefinition, val canonical: BlockPos) : Outcome

        data class AwaitDestination(val role: RoleDefinition) : Outcome

        data class Refuse(val messageKey: String, val arg: String? = null) : Outcome
    }

    /** Another Pokémon that is bound to the target: its display name, its species name, and whether the clicking player owns it. */
    data class Bound(val name: String, val species: String, val ownedByPlayer: Boolean)

    /**
     * For a click with a pending source, [fittingRoles], [insideWorkArea] and [boundToOther] describe the source, and
     * [canonical] and [destinationInReach] describe the destination.
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
        val boundToOther: Bound?,
        val pending: PendingSource?,
        val sameAsPending: Boolean,
        /** The bound role's handler accepts the destination's distance from the pasture block (CD-TLK-12). */
        val destinationInReach: Boolean = true,
    )

    fun evaluate(input: Input, shapeOf: (RoleDefinition) -> BindingShape): Outcome {
        if (input.selection == null || !input.pokemonOwnedByPlayer) return Outcome.Refuse(NO_SELECTION)
        if (!input.pastureInWorkMode) return Outcome.Refuse(NOT_WORK_MODE)
        // CD-LSH-16: one click gives one predictable role.
        val role = RoleSelection.best(input.fittingRoles) ?: return Outcome.Refuse(NO_ROLE)
        val twoTargets = shapeOf(role) == BindingShape.SOURCE_AND_DESTINATION
        if (twoTargets && input.pending != null && input.sameAsPending) return Outcome.Refuse(DESTINATION_SAME)
        // CD-TLK-12: the destination may sit outside the work area, but not past the role's range from the pasture block.
        if (twoTargets && input.pending != null && !input.destinationInReach) return Outcome.Refuse(TOO_FAR)
        // The source of a two-target role is checked at its own click, so a refusal never waits for the second click.
        if (!input.insideWorkArea) return Outcome.Refuse(OUTSIDE_AREA)
        // CD-LSH-05: another player's Pokémon is named by its species, so its nickname never reaches the clicking player.
        input.boundToOther?.let { bound ->
            return if (bound.ownedByPlayer) {
                Outcome.Refuse(ALREADY_ASSIGNED, bound.name)
            } else {
                Outcome.Refuse(ALREADY_ASSIGNED_OTHER, bound.species)
            }
        }
        return if (twoTargets && input.pending == null) Outcome.AwaitDestination(role) else Outcome.Bind(role, input.canonical)
    }
}
