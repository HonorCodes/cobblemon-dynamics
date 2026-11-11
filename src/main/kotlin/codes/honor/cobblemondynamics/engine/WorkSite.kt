package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.config.EngineSettings
import codes.honor.cobblemondynamics.pasture.PastureKey
import net.minecraft.server.level.ServerLevel
import java.util.UUID

/** Where workers work: a pasture in Work Mode or one Quick Work order (ADR-0013). The engine's per-worker steps take either. */
sealed interface WorkSite {
    val level: ServerLevel

    /** The pasture that holds the site's claims, or null for a Quick Work order, which no pasture holds. */
    val pastureKey: PastureKey?

    /** In the order the workers joined. */
    val slots: MutableMap<UUID, WorkerSlot>

    /** The box in which a worker of the site finds its work spot. */
    fun area(settings: EngineSettings): WorkArea
}
