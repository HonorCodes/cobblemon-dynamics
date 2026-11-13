package codes.honor.cobblemondynamics.leash

import codes.honor.cobblemondynamics.config.EngineSettings
import codes.honor.cobblemondynamics.engine.WorkArea
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
    const val FULL = "cobblemon_dynamics.leash.full"
    const val FULL_ONE = "cobblemon_dynamics.leash.full_one"

    sealed interface Outcome {
        data class Bind(val role: RoleDefinition, val canonical: BlockPos) : Outcome

        data class AwaitDestination(val role: RoleDefinition) : Outcome

        /** [at] is the container that a [FULL] or [FULL_ONE] refusal names (CD-LSH-29). */
        data class Refuse(val messageKey: String, val arg: String? = null, val at: BlockPos? = null) : Outcome
    }

    /**
     * Another Pokémon whose binding of an exclusive role holds the target: its display name, its species name, and whether the clicking
     * player owns it.
     */
    data class Bound(val name: String, val species: String, val ownedByPlayer: Boolean)

    /** CD-LSH-29: a container that a binding of a logistics role names, and the places that other bindings take there. */
    data class Place(val container: BlockPos, val taken: Int)

    /**
     * For a click with a pending source, [fittingRoles], [insideWorkArea] and [boundToOtherOf] describe the source, and
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
        /**
         * The other Pokémon whose binding holds the target in the lane of the given role, or null. A farming binding holds only its own
         * role's lane, so a Cultivator and a Harvester bind to one crop (CD-CRP-07, CD-CRP-08).
         */
        val boundToOtherOf: (RoleDefinition) -> Bound?,
        val pending: PendingSource?,
        val sameAsPending: Boolean,
        /** The bound role's handler accepts the destination's distance from the pasture block (CD-TLK-12). */
        val destinationInReach: Boolean = true,
        /** The handler's refusal when the worker has no spot at the target, or null when it has one or the handler binds anyway (CD-FAN-04). */
        val noSpotRefusal: String? = null,
        /** The Pokémon's types, primary first, which pick between the fitting roles of one handler (ruling 1 of the Phase 2 plan). */
        val types: List<String> = emptyList(),
        /** For a logistics role, each container that the binding names, source first (CD-LSH-29). */
        val places: List<Place> = emptyList(),
        /** `logistics.maxWorkersPerContainer` (CD-CFG-19). */
        val maxWorkers: Int = EngineSettings.DEFAULT.maxWorkersPerContainer,
    )

    /** What a block click does with the leash's pending source before it reads any block of it (CD-SEC-08). */
    enum class PendingCheck {
        /** The click may read the source. */
        READ,

        /** The click refuses with "Outside its pasture's work area" (CD-TLK-09), and the source leaves the stack. */
        OUTSIDE_AREA,

        /** The source counts as absent and leaves the stack, as an expired one does. */
        ABSENT,

        /** The Pokémon has no Work Mode pasture, so the click reads nothing of the source and the rules refuse it. */
        NO_AREA,
    }

    /**
     * A client in creative mode can write the stack, so a click reads a pending source only where a source click could have stored it:
     * inside [area], in a chunk that [loaded] reports. The check reads no block, so it never loads a chunk.
     */
    fun checkPending(source: BlockPos, area: WorkArea?, loaded: (BlockPos) -> Boolean): PendingCheck = when {
        area == null -> PendingCheck.NO_AREA
        !area.contains(source) -> PendingCheck.OUTSIDE_AREA
        !loaded(source) -> PendingCheck.ABSENT
        else -> PendingCheck.READ
    }

    /** [sharesOf] tells a logistics role, whose containers other bindings share (CD-LSH-28). */
    fun evaluate(input: Input, shapeOf: (RoleDefinition) -> BindingShape, sharesOf: (RoleDefinition) -> Boolean = { false }): Outcome {
        if (input.selection == null || !input.pokemonOwnedByPlayer) return Outcome.Refuse(NO_SELECTION)
        if (!input.pastureInWorkMode) return Outcome.Refuse(NOT_WORK_MODE)
        // CD-LSH-16: one click gives one predictable role.
        val role = RoleSelection.best(input.fittingRoles, input.types) ?: return Outcome.Refuse(NO_ROLE)
        val twoTargets = shapeOf(role) == BindingShape.SOURCE_AND_DESTINATION
        if (twoTargets && input.pending != null && input.sameAsPending) return Outcome.Refuse(DESTINATION_SAME)
        // CD-TLK-12: the destination may sit outside the work area, but not past the role's range from the pasture block.
        if (twoTargets && input.pending != null && !input.destinationInReach) return Outcome.Refuse(TOO_FAR)
        // The source of a two-target role is checked at its own click, so a refusal never waits for the second click.
        if (!input.insideWorkArea) return Outcome.Refuse(OUTSIDE_AREA)
        if (sharesOf(role)) {
            input.places.firstOrNull { it.taken >= input.maxWorkers }?.let { full ->
                val key = if (input.maxWorkers == 1) FULL_ONE else FULL
                return Outcome.Refuse(key, input.maxWorkers.toString(), full.container)
            }
        } else {
            // CD-LSH-05: another player's Pokémon is named by its species, so its nickname never reaches the clicking player.
            input.boundToOtherOf(role)?.let { bound ->
                return if (bound.ownedByPlayer) {
                    Outcome.Refuse(ALREADY_ASSIGNED, bound.name)
                } else {
                    Outcome.Refuse(ALREADY_ASSIGNED_OTHER, bound.species)
                }
            }
        }
        input.noSpotRefusal?.let { return Outcome.Refuse(it) }
        return if (twoTargets && input.pending == null) Outcome.AwaitDestination(role) else Outcome.Bind(role, input.canonical)
    }
}
