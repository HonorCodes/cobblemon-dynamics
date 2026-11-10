package codes.honor.cobblemondynamics.role

object RoleSelection {
    fun isEligible(role: RoleDefinition, pokemonTypes: Collection<String>): Boolean = pokemonTypes.any { it.lowercase() in role.types }

    private val ORDER = compareByDescending<RoleDefinition> { it.priority }.thenBy { it.id.toString() }

    /** CD-ASG-16, CD-LSH-16: the higher priority wins, then the role id that sorts first. */
    fun best(candidates: Collection<RoleDefinition>): RoleDefinition? = candidates.minWithOrNull(ORDER)
}
