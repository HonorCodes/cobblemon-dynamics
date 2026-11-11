package codes.honor.cobblemondynamics.role

object RoleSelection {
    fun isEligible(role: RoleDefinition, pokemonTypes: Collection<String>): Boolean = pokemonTypes.any { it.lowercase() in role.types }

    private val ORDER = compareByDescending<RoleDefinition> { it.priority }.thenBy { it.id.toString() }

    /**
     * CD-ASG-16, CD-LSH-16: the higher priority wins, then the role id that sorts first. Between the roles of the winner's handler and
     * priority, such as the Fan Catalyst files, the role of the Pokémon's primary type wins, then that of its secondary type (ruling 1
     * of the Phase 2 plan), so [types] go primary first. With no types, the id order decides there too.
     */
    fun best(candidates: Collection<RoleDefinition>, types: List<String> = emptyList()): RoleDefinition? {
        val first = candidates.minWithOrNull(ORDER) ?: return null
        val rank = { role: RoleDefinition -> types.indexOfFirst { it.lowercase() in role.types }.takeIf { it >= 0 } ?: types.size }
        return candidates.filter { it.handler == first.handler && it.priority == first.priority }
            .minWithOrNull(compareBy(rank).thenBy { it.id.toString() })
    }
}
