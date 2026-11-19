package codes.honor.cobblemondynamics.role

object RoleSelection {
    /**
     * CD-ASG-02, CD-PCK-02, CD-PCK-03: one of the Cobblemon's types is a type of [role], or, for a role of `type_match` `all`, each type
     * of the role is one of the Cobblemon's. Every check of a role's types asks this.
     */
    fun isEligible(role: RoleDefinition, pokemonTypes: Collection<String>): Boolean {
        if (!role.allTypes) return pokemonTypes.any { it.lowercase() in role.types }
        val own = pokemonTypes.mapTo(HashSet()) { it.lowercase() }
        return role.types.isNotEmpty() && own.containsAll(role.types)
    }

    /**
     * CD-PCK-04: a binding of [role] still fits a Cobblemon of [pokemonTypes]: the role is of its types, and a leash may bind it. A data
     * pack that sets `leash` to false on a bound role ends its bindings as a change of form does (CD-LSH-14).
     */
    fun bindingFits(role: RoleDefinition, pokemonTypes: Collection<String>): Boolean = role.leash && isEligible(role, pokemonTypes)

    private val ORDER = compareByDescending<RoleDefinition> { it.priority }.thenBy { it.id.toString() }

    /** Two roles of one handler, such as the Fan Catalyst files, which pick by type (ruling 1 of the Phase 2 plan). */
    val SAME_HANDLER: (RoleDefinition, RoleDefinition) -> Boolean = { a, b -> a.handler == b.handler }

    /**
     * CD-LSH-16: the higher priority wins, then the role id that sorts first. Between the roles of the winner's group and priority, the
     * role of the Pokémon's primary type wins, then that of its secondary type (ruling 1 of the Phase 2 plan), so [types] go primary
     * first. With no types, the id order decides there too. A group is the roles of one handler, and [sameGroup] may join more, as the
     * two farming roles join for the primary type (CD-CRP-22). The bind asks this with no demand, so the bound role never depends on the
     * target's state at the click.
     */
    fun best(
        candidates: Collection<RoleDefinition>,
        types: List<String> = emptyList(),
        sameGroup: (RoleDefinition, RoleDefinition) -> Boolean = SAME_HANDLER,
    ): RoleDefinition? {
        val first = candidates.minWithOrNull(ORDER) ?: return null
        val rank = { role: RoleDefinition -> types.indexOfFirst { it.lowercase() in role.types }.takeIf { it >= 0 } ?: types.size }
        val group = candidates.filter { sameGroup(it, first) && it.priority == first.priority }
        if (group.size == 1) return first
        return group.minWithOrNull(compareBy(rank).thenBy { it.id.toString() })
    }

    /**
     * CD-ASG-16, CD-QWK-03, CD-CRP-21, R94: [best] of the candidates with [hasDemand] on the target, or of all of them when none has
     * demand. One candidate asks no demand, so a worker that fits one role of a target costs what it did before R94.
     */
    fun withWork(
        candidates: Collection<RoleDefinition>,
        types: List<String> = emptyList(),
        sameGroup: (RoleDefinition, RoleDefinition) -> Boolean = SAME_HANDLER,
        hasDemand: (RoleDefinition) -> Boolean,
    ): RoleDefinition? {
        if (candidates.size < 2) return best(candidates, types, sameGroup)
        return best(candidates.filter(hasDemand).ifEmpty { candidates }, types, sameGroup)
    }
}
