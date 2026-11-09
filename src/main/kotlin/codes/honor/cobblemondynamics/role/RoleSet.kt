package codes.honor.cobblemondynamics.role

import codes.honor.cobblemondynamics.handler.TargetHandler
import net.minecraft.resources.ResourceLocation

data class ActiveRole(val definition: RoleDefinition, val handler: TargetHandler)

/** The roles that can work: parsed, not disabled in the config, and with a registered handler. */
class RoleSet(
    roles: Map<ResourceLocation, RoleDefinition>,
    handlers: (ResourceLocation) -> TargetHandler?,
    disabled: Set<ResourceLocation>,
    log: (String) -> Unit,
) {
    /** Sorted by role id. */
    val active: List<ActiveRole> = roles.values
        .filter { it.id !in disabled }
        .sortedBy { it.id.toString() }
        .mapNotNull { definition ->
            val handler = handlers(definition.handler)
            if (handler == null) log("Role ${definition.id} is disabled: handler ${definition.handler} is not registered")
            handler?.let { ActiveRole(definition, it) }
        }

    private val byId: Map<ResourceLocation, ActiveRole> = active.associateBy { it.definition.id }

    fun get(id: ResourceLocation): ActiveRole? = byId[id]

    fun forTypes(types: Collection<String>): List<ActiveRole> = active.filter { RoleSelection.isEligible(it.definition, types) }

    companion object {
        val EMPTY = RoleSet(emptyMap(), { null }, emptySet()) { }
    }
}
