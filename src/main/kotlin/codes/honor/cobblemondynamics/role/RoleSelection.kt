package codes.honor.cobblemondynamics.role

object RoleSelection {
    fun isEligible(role: RoleDefinition, pokemonTypes: Collection<String>): Boolean = pokemonTypes.any { it.lowercase() in role.types }

    private val ORDER = compareByDescending<RoleDefinition> { it.priority }.thenBy { it.id.toString() }

    /** Two roles of one handler, such as the Fan Catalyst files, which pick by type (ruling 1 of the Phase 2 plan). */
    val SAME_HANDLER: (RoleDefinition, RoleDefinition) -> Boolean = { a, b -> a.handler == b.handler }

    /**
     * CD-ASG-16, CD-LSH-16: the higher priority wins, then the role id that sorts first. Between the roles of the winner's group and
     * priority, the role of the Pokémon's primary type wins, then that of its secondary type (ruling 1 of the Phase 2 plan), so [types]
     * go primary first. With no types, the id order decides there too. A group is the roles of one handler, and [sameGroup] may join
     * more, as the two farming roles join for the primary type (CD-CRP-22). Within the group, a role with [hasDemand] on the target
     * comes first, which Quick Work asks (CD-CRP-21).
     */
    fun best(
        candidates: Collection<RoleDefinition>,
        types: List<String> = emptyList(),
        sameGroup: (RoleDefinition, RoleDefinition) -> Boolean = SAME_HANDLER,
        hasDemand: (RoleDefinition) -> Boolean = { true },
    ): RoleDefinition? {
        val first = candidates.minWithOrNull(ORDER) ?: return null
        val rank = { role: RoleDefinition -> types.indexOfFirst { it.lowercase() in role.types }.takeIf { it >= 0 } ?: types.size }
        val group = candidates.filter { sameGroup(it, first) && it.priority == first.priority }
        if (group.size == 1) return first
        return group.minWithOrNull(compareBy<RoleDefinition> { if (hasDemand(it)) 0 else 1 }.thenBy(rank).thenBy { it.id.toString() })
    }
}
