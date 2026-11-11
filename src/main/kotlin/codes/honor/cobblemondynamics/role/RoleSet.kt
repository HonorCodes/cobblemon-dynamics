package codes.honor.cobblemondynamics.role

import codes.honor.cobblemondynamics.handler.TargetHandler
import net.minecraft.resources.ResourceLocation

data class ActiveRole(val definition: RoleDefinition, val handler: TargetHandler)

/**
 * The roles that can work: parsed, not disabled in the config, and with a registered handler. [carried] holds the roles that an
 * earlier set removed, with their reasons, so that [disabled] names every role that does not work (CD-CMP-10).
 */
class RoleSet private constructor(
    roles: Map<ResourceLocation, RoleDefinition>,
    handlers: (ResourceLocation) -> TargetHandler?,
    disabled: Set<ResourceLocation>,
    carried: Map<ResourceLocation, String>,
    log: (String) -> Unit,
) {
    constructor(
        roles: Map<ResourceLocation, RoleDefinition>,
        handlers: (ResourceLocation) -> TargetHandler?,
        disabled: Set<ResourceLocation>,
        log: (String) -> Unit,
    ) : this(roles, handlers, disabled, emptyMap(), log)

    private val reasons = LinkedHashMap(carried)

    /** Sorted by role id. */
    val active: List<ActiveRole> = roles.values
        .filter { definition ->
            val on = definition.id !in disabled
            if (!on) reasons[definition.id] = "roles.disabled"
            on
        }
        .sortedBy { it.id.toString() }
        .mapNotNull { definition ->
            val handler = handlers(definition.handler)
            if (handler == null) {
                log("Role ${definition.id} is disabled: handler ${definition.handler} is not registered")
                reasons[definition.id] = "handler ${definition.handler} is not registered"
            }
            handler?.let { ActiveRole(definition, it) }
        }

    /** Each role that does not work, with its reason: `roles.disabled`, a handler that is not registered, or a mixin that did not apply. */
    val disabled: Map<ResourceLocation, String> = reasons

    private val byId: Map<ResourceLocation, ActiveRole> = active.associateBy { it.definition.id }

    fun get(id: ResourceLocation): ActiveRole? = byId[id]

    fun forTypes(types: Collection<String>): List<ActiveRole> = active.filter { RoleSelection.isEligible(it.definition, types) }

    /** This set without the roles of [handlers], each of which [disabled] then names with [reason] of its handler. */
    fun withoutHandlers(handlers: Set<ResourceLocation>, reason: (ResourceLocation) -> String): RoleSet {
        val (removed, kept) = active.partition { it.definition.handler in handlers }
        val byHandler = kept.associate { it.definition.handler to it.handler }
        val reasons = disabled + removed.associate { it.definition.id to reason(it.definition.handler) }
        return RoleSet(kept.associate { it.definition.id to it.definition }, byHandler::get, emptySet(), reasons) { }
    }

    companion object {
        val EMPTY = RoleSet(emptyMap(), { null }, emptySet()) { }
    }
}
