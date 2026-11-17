package codes.honor.cobblemondynamics.compat.create_enchantment_industry

import codes.honor.cobblemondynamics.OnceLogger
import codes.honor.cobblemondynamics.handler.vanilla.MuseStations
import com.simibubi.create.content.processing.basin.BasinBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel

/**
 * CD-MCH-06: CEI's Infuser as a station of the Muse. Its demand reads the basin below the Infuser with Create's class, as CEI's own
 * `getBasin` does: CEI needs Create, so the class is there whenever this one loads. A Create that renamed it would turn off this demand
 * alone, with one warning, and not the Muse's handler, which the engine's guard would turn off on the whole server.
 */
object CeiInfuserTarget {
    private val warnings = OnceLogger()

    val STATION = MuseStations.Station(CeiInfuserRule.STATS_BELOW, ::hasDemand)

    fun hasDemand(level: ServerLevel, infuser: BlockPos): Boolean = try {
        CeiInfuserRule.hasDemand(level.getBlockEntity(CeiInfuserRule.basinPos(infuser)) is BasinBlockEntity)
    } catch (e: LinkageError) {
        warnings.warn(STATION) { "The Muse leaves CEI's Infusers, because Create's basin did not load: $e" }
        false
    }
}
