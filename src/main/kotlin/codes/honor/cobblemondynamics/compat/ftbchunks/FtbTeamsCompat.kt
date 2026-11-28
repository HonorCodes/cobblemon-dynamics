package codes.honor.cobblemondynamics.compat.ftbchunks

import codes.honor.cobblemondynamics.compat.TeamAllies
import codes.honor.cobblemondynamics.compat.TeamAllyCheck
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import net.minecraft.server.MinecraftServer
import java.util.UUID

/**
 * Teammate checks via FTB Teams (CD-TEAM-01 to CD-TEAM-03, CD-TEAM-11). Lives in `compat/ftbchunks/` so all `dev.ftb.` imports stay
 * inside the FTB compat package (CD-QWK-33), and registers behind `ModList.isLoaded("ftbteams")`.
 */
object FtbTeamsCompat {
    fun register() = TeamAllies.register("ftbteams", Check)

    private object Check : TeamAllyCheck {
        override fun areTeammates(server: MinecraftServer, a: UUID, b: UUID): Boolean {
            val api = FTBTeamsAPI.api()
            return api.isManagerLoaded && api.manager.arePlayersInSameTeam(a, b)
        }
    }
}
