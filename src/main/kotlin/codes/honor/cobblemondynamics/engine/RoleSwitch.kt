package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.handler.ClaimMemo
import codes.honor.cobblemondynamics.role.RoleDefinition
import codes.honor.cobblemondynamics.role.RoleSelection
import net.minecraft.resources.ResourceLocation

/**
 * R94: a worker of two types does each of its roles that has work on its target (ADR-0005 amendment 4, ADR-0006). The binding keeps the
 * role of its bind, and the switch is runtime state of the worker's slot. The engine reads every input from server state.
 */
object RoleSwitch {
    /**
     * CD-LSH-33, CD-ASG-22: the roles that the worker may take on its target, [own] first: the role of its binding, or of its auto claim.
     * Each other role of [enabled], the enabled roles of the worker's types, passes when its handler is not [own]'s, it names one target
     * whose claims share no target ([single]), its handler [matches] the cell and resolves it to itself ([resolvesToItself]), and, for a
     * worker with no binding ([bound] false), its auto-assign flag is true. Last, its handler [accepts] the cell for that role: it would
     * not refuse a bind there and holds the cell valid, so a tree that no role fells keeps no binding. An [own] role that is not [single]
     * has no switch roles, and the list is empty. The cheap checks run first, so a role of another handler costs no block read.
     */
    fun rolesOf(
        own: RoleDefinition,
        enabled: List<RoleDefinition>,
        bound: Boolean,
        single: (RoleDefinition) -> Boolean,
        matches: (RoleDefinition) -> Boolean,
        resolvesToItself: (RoleDefinition) -> Boolean,
        accepts: (RoleDefinition) -> Boolean = { true },
    ): List<RoleDefinition> {
        if (!single(own)) return emptyList()
        val others = enabled.filter { role ->
            role.id != own.id &&
                role.handler != own.handler &&
                (bound || role.autoAssign) &&
                single(role) &&
                matches(role) &&
                resolvesToItself(role) &&
                accepts(role)
        }
        return listOf(own) + others
    }

    /**
     * CD-LSH-30, CD-LSH-32, CD-ASG-21: [current] while it has demand, so a started fell completes and no role flips back and forth.
     * Otherwise the first of the other [roles] in the order of CD-LSH-16 whose lane is free for the worker, whose key the worker does not
     * skip or stall, and which has demand. Null keeps [current].
     */
    fun choose(
        current: RoleDefinition,
        roles: List<RoleDefinition>,
        types: List<String>,
        hasDemand: (RoleDefinition) -> Boolean,
        laneFree: (RoleDefinition) -> Boolean,
        skipped: (RoleDefinition) -> Boolean,
        sameGroup: (RoleDefinition, RoleDefinition) -> Boolean = RoleSelection.SAME_HANDLER,
    ): RoleDefinition? {
        if (hasDemand(current)) return current
        val open = roles.filter { it.id != current.id && laneFree(it) && !skipped(it) && hasDemand(it) }
        return RoleSelection.best(open, types, sameGroup)
    }

    /**
     * CD-LSH-31: a binding holds while its own handler keeps the target ([ownHolds]), or while a switch role finds a target of its own on
     * the bound cell. [switchRoles] runs only when the own handler lets the target go.
     */
    fun bindingHolds(ownHolds: Boolean, switchRoles: () -> List<RoleDefinition>): Boolean = ownHolds || switchRoles().size > 1

    /** CD-ASG-04: a Fairy-type with no binding heals, so a switch never gives it a machine. A bound worker switches. */
    fun maySwitch(bound: Boolean, hasFairyType: Boolean): Boolean = bound || !hasFairyType
}

/**
 * One cached answer of the switch checks, which can search a whole tree: [get] computes again only when its key changes. The key holds
 * the block state at the target, so the answer lasts until the block at the target changes.
 */
class SwitchCache<T : Any> {
    private var held: Pair<Any, T>? = null

    fun get(key: Any, compute: () -> T): T = held?.takeIf { it.first == key }?.second ?: compute().also { held = key to it }
}

/**
 * CD-LSH-34: one handler memo for each switch role of a worker, for the demand checks of the switch. The memos last while [follow] gets
 * the same key: the binding's target and role, or the target of an auto claim. A release of an auto worker [clear]s them.
 */
class SwitchProbes {
    private val memos = HashMap<ResourceLocation, ClaimMemo>(2)
    private var key: Any? = null

    fun follow(key: Any?) {
        if (key == this.key) return
        this.key = key
        memos.clear()
    }

    fun memo(roleId: ResourceLocation): ClaimMemo = memos.getOrPut(roleId) { ClaimMemo() }

    fun clear() {
        memos.clear()
    }
}
