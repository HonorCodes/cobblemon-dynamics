package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.fx.WorkFocusEntity
import codes.honor.cobblemondynamics.handler.ClaimMemo
import codes.honor.cobblemondynamics.leash.Binding
import codes.honor.cobblemondynamics.role.ActiveRole
import codes.honor.cobblemondynamics.stamina.RecoveryRule
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon

/**
 * One worker with the live objects that its tick needs. [role] and [memo] belong to the worker's current work claim, and [focus] is
 * set while it is Working. [binding] is the leash binding that each poll reads from the Pokémon, when it names this pasture. A Quick
 * Worker has no poll, so it has none.
 */
class WorkerSlot(val worker: Worker, var entity: PokemonEntity, var pokemon: Pokemon) {
    var role: ActiveRole? = null
    var memo: ClaimMemo = ClaimMemo()
    var focus: WorkFocusEntity? = null
    var binding: Binding? = null

    /** How the worker recovers, from its types and its binding at each poll (CD-HEAL-13). */
    var rule: RecoveryRule = RecoveryRule.WORKER

    /** The state and tick of the last flush of Work Stamina, null before the first one. */
    var flushedStatus: WorkerStatus? = null
    var flushedAt: Long = 0

    /** The last `stamina_view` that the engine set, and the entity it set it on. A new entity has none yet. */
    var viewEntity: PokemonEntity? = null
    var viewPercent: Double = 0.0
    var viewStatus: WorkerStatus = WorkerStatus.IDLE
}
