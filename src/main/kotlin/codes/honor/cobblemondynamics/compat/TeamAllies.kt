package codes.honor.cobblemondynamics.compat

import codes.honor.cobblemondynamics.OnceLogger
import net.minecraft.server.MinecraftServer
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** One optional party or team mod's teammate question (CD-TEAM-01 to CD-TEAM-03, CD-TEAM-11). */
fun interface TeamAllyCheck {
    fun areTeammates(server: MinecraftServer, a: UUID, b: UUID): Boolean
}

/**
 * Evaluates registered teammate providers, returning true when any provider confirms membership and catching `Exception` and
 * `LinkageError` with one warning per mod and error class (CD-TEAM-11).
 */
class TeamChecksGuard(private val warnings: OnceLogger) {
    fun <C> anyTeammate(a: UUID, b: UUID, checks: List<Pair<String, C>>, ask: (C) -> Boolean): Boolean {
        if (a == b) return true
        return checks.any { (id, check) ->
            try {
                ask(check)
            } catch (e: Exception) {
                warn(id, e)
            } catch (e: LinkageError) {
                warn(id, e)
            }
        }
    }

    private fun warn(id: String, e: Throwable): Boolean {
        warnings.warn("$id:${e.javaClass.name}") { "Pasture team check of $id fails: $e" }
        return false
    }
}

/** Registered optional party/team mods. Names no optional mod class directly (CD-TEAM-11). */
object TeamAllies {
    private val checks = CopyOnWriteArrayList<Pair<String, TeamAllyCheck>>()
    private val guard = TeamChecksGuard(OnceLogger())

    fun register(modId: String, check: TeamAllyCheck) {
        checks += modId to check
    }

    fun areTeammates(server: MinecraftServer, a: UUID, b: UUID): Boolean = guard.anyTeammate(a, b, checks) { it.areTeammates(server, a, b) }
}
