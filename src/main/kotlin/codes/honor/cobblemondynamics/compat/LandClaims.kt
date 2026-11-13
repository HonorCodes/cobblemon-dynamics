package codes.honor.cobblemondynamics.compat

import codes.honor.cobblemondynamics.OnceLogger
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The questions of one claim mod. Each asks about one block, with an empty main hand: the use questions about the canonical block of a
 * target, and the break questions about each block that a harvest breaks (CD-HRV-16).
 */
interface LandClaimCheck {
    /** Quick Work: may the online [owner] right-click [pos] (CD-QWK-26). The claim mod may show its own text. */
    fun mayUse(owner: ServerPlayer, level: ServerLevel, pos: BlockPos): Boolean

    /**
     * A pasture: may the player [ownerId], online or not, use [pos] (CD-LND-05). Null is a pasture with no placer. The check sends no
     * message and counts no attempt (CD-LND-10).
     */
    fun ownerMayUse(ownerId: UUID?, level: ServerLevel, pos: BlockPos): Boolean

    /** A harvest that breaks [pos], for a Quick Worker: may the online [owner] break it (CD-HRV-16). The claim mod may show its text. */
    fun mayBreak(owner: ServerPlayer, level: ServerLevel, pos: BlockPos): Boolean

    /**
     * A harvest that breaks [pos], for a pasture worker: may the player [ownerId], online or not, break it. Null is a pasture with no
     * placer. The check sends no message, counts no attempt and posts no event (CD-HRV-21).
     */
    fun ownerMayBreak(ownerId: UUID?, level: ServerLevel, pos: BlockPos): Boolean
}

/** Who asks, for the warning of a check that throws (CD-QWK-30, CD-LND-11). */
enum class ClaimAsker(val refusal: String) {
    QUICK_WORK("Quick Work refuses orders"),
    PASTURE("Pastures work no target"),
    HARVEST("Harvests break no block"),
}

/**
 * Every check must allow, in order, and the first refusal ends the questions. A check that throws refuses, with one warning for each
 * asker, claim mod and class of error (CD-QWK-30, CD-LND-11).
 */
class LandClaimGate(private val warnings: OnceLogger) {
    fun <C> allows(asker: ClaimAsker, checks: List<Pair<String, C>>, ask: (C) -> Boolean): Boolean = checks.all { (id, check) ->
        try {
            ask(check)
        } catch (e: Exception) {
            refuse(asker, id, e)
        } catch (e: LinkageError) {
            refuse(asker, id, e)
        }
    }

    private fun refuse(asker: ClaimAsker, id: String, e: Throwable): Boolean {
        warnings.warn("$asker:$id:${e.javaClass.name}") { "${asker.refusal} while the claim check of $id fails: $e" }
        return false
    }
}

/**
 * The claim mods that Quick Work and the pastures ask (CD-QWK-26, CD-QWK-29, CD-LND-01 to CD-LND-04). Mod construction fills the
 * list, as it fills HandlerRegistry, and nothing changes it later, so it holds no state of a world. This file names no class of a
 * claim mod.
 */
object LandClaims {
    private val checks = CopyOnWriteArrayList<Pair<String, LandClaimCheck>>()
    private val gate = LandClaimGate(OnceLogger())

    fun register(modId: String, check: LandClaimCheck) {
        checks += modId to check
    }

    fun mayUse(owner: ServerPlayer, level: ServerLevel, pos: BlockPos): Boolean =
        gate.allows(ClaimAsker.QUICK_WORK, checks) { it.mayUse(owner, level, pos) }

    /** CD-LND-05: by the UUID of the pasture's placer, never a player, so an offline placer gets the answer of an online one. */
    fun ownerMayUse(ownerId: UUID?, level: ServerLevel, pos: BlockPos): Boolean =
        gate.allows(ClaimAsker.PASTURE, checks) { it.ownerMayUse(ownerId, level, pos) }

    /** CD-HRV-16, CD-HRV-20: the break question of a Quick Worker's harvest, for its online owner. A check that throws refuses. */
    fun mayBreak(owner: ServerPlayer, level: ServerLevel, pos: BlockPos): Boolean =
        gate.allows(ClaimAsker.HARVEST, checks) { it.mayBreak(owner, level, pos) }

    /** CD-HRV-16, CD-HRV-21: the break question of a pasture worker's harvest, by the UUID of the pasture's placer, silent. */
    fun ownerMayBreak(ownerId: UUID?, level: ServerLevel, pos: BlockPos): Boolean =
        gate.allows(ClaimAsker.HARVEST, checks) { it.ownerMayBreak(ownerId, level, pos) }
}
