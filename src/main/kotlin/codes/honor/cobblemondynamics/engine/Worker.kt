package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.claim.TargetKey
import codes.honor.cobblemondynamics.pasture.PastureKey
import codes.honor.cobblemondynamics.stamina.Stamina
import net.minecraft.core.BlockPos
import java.util.UUID

/** The engine's runtime state of one tethered Pokémon. [WorkerMachine] makes every status change. */
class Worker(val pokemonId: UUID, var pasture: PastureKey) {
    var status: WorkerStatus = WorkerStatus.IDLE
    var leashed: Boolean = false

    /** The live Work Stamina. The engine loads it when the worker joins and flushes it to the Pokémon's saved data. */
    var stamina: Stamina = Stamina()

    /** [stamina] holds every tick before this one, so no tick counts twice and a flush saves this tick as `last_game_time`. */
    var staminaUntil: Long = 0

    /** Tired is the Work Stamina's flag: set at 0% and cleared at 100% (CD-STA-08, CD-STA-10). */
    var tired: Boolean
        get() = stamina.tired
        set(value) {
            stamina.tired = value
        }

    /** The tick of the current work claim. */
    var claimTick: Long = -1

    /** The canonical target of the current work claim, and the standable cell that the worker works it from. */
    var target: BlockPos? = null
    var spot: BlockPos? = null

    /** The tick of the last status change. */
    var statusSince: Long = 0

    /** The leashed retry after a missed arrival, or -1 when the worker is not waiting. */
    var retryAt: Long = -1
    var missedArrival: Boolean = false

    /** Targets that this worker skips until a tick, after a travel timeout (CD-ASG-14). */
    val skipUntil: MutableMap<TargetKey, Long> = HashMap()

    fun isSkipped(key: TargetKey, now: Long): Boolean = now < (skipUntil[key] ?: Long.MIN_VALUE)
}
