package codes.honor.cobblemondynamics.compat

import codes.honor.cobblemondynamics.OnceLogger
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import java.util.concurrent.CopyOnWriteArrayList

/** The question of one claim mod: may [owner] right-click [pos] in [level] with an empty main hand (CD-QWK-26). */
fun interface LandClaimCheck {
    fun mayUse(owner: ServerPlayer, level: ServerLevel, pos: BlockPos): Boolean
}

/** Every check must allow, in order, and the first refusal ends the questions. A check that throws refuses (CD-QWK-30). */
class LandClaimGate(private val warnings: OnceLogger) {
    fun <C> allows(checks: List<Pair<String, C>>, ask: (C) -> Boolean): Boolean = checks.all { (id, check) ->
        try {
            ask(check)
        } catch (e: Exception) {
            refuse(id, e)
        } catch (e: LinkageError) {
            refuse(id, e)
        }
    }

    private fun refuse(id: String, e: Throwable): Boolean {
        warnings.warn("$id:${e.javaClass.name}") { "Quick Work refuses orders while the claim check of $id fails: $e" }
        return false
    }
}

/**
 * The claim mods that Quick Work asks (CD-QWK-26, CD-QWK-29). Mod construction fills the list, as it fills HandlerRegistry, and
 * nothing changes it later, so it holds no state of a world. This file names no class of a claim mod.
 */
object LandClaims {
    private val checks = CopyOnWriteArrayList<Pair<String, LandClaimCheck>>()
    private val gate = LandClaimGate(OnceLogger())

    fun register(modId: String, check: LandClaimCheck) {
        checks += modId to check
    }

    fun mayUse(owner: ServerPlayer, level: ServerLevel, pos: BlockPos): Boolean = gate.allows(checks) { it.mayUse(owner, level, pos) }
}
