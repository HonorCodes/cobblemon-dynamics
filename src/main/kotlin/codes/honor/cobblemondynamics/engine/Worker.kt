package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.bed.BedCandidate
import codes.honor.cobblemondynamics.claim.TargetKey
import codes.honor.cobblemondynamics.pasture.PastureKey
import codes.honor.cobblemondynamics.stamina.Stamina
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * The engine's runtime state of one tethered Pokémon, or of a party Pokémon on a Quick Work order, whose [pasture] is null
 * (ADR-0013). [WorkerMachine] makes every status change.
 */
class Worker(val pokemonId: UUID, var pasture: PastureKey?) {
    var status: WorkerStatus = WorkerStatus.IDLE
    var leashed: Boolean = false

    var stamina: Stamina = Stamina()

    var staminaUntil: Long = 0

    var tired: Boolean
        get() = stamina.tired
        set(value) {
            stamina.tired = value
        }

    var claimTick: Long = -1
    var travelTimeoutTicks: Int = 0

    var target: BlockPos? = null
    var spot: BlockPos? = null
    var spotTopY: Double = 0.0

    var bed: BedCandidate? = null

    var restSpot: Vec3? = null

    var statusSince: Long = 0

    var retryAt: Long = -1
    var missedArrival: Boolean = false

    val skipUntil: MutableMap<TargetKey, Long> = HashMap()

    val stalledUntil: MutableMap<TargetKey, Long> = HashMap()

    val retryMisses: MutableMap<TargetKey, Int> = HashMap()

    fun isSkipped(key: TargetKey, now: Long): Boolean = now < (skipUntil[key] ?: Long.MIN_VALUE) || isStalled(key, now)

    fun isStalled(key: TargetKey, now: Long): Boolean = now < (stalledUntil[key] ?: Long.MIN_VALUE)

    fun recordMiss(key: TargetKey, maxRetryWaitTicks: Int): Int {
        val count = (retryMisses[key] ?: 0) + 1
        retryMisses[key] = count
        return TravelTiming.retryTicks(count, maxRetryWaitTicks)
    }

    fun recordArrival(key: TargetKey?) {
        if (key != null) retryMisses.remove(key)
    }

    fun dropExpired(now: Long) {
        skipUntil.values.removeIf { it <= now }
        stalledUntil.values.removeIf { it <= now }
    }
}
