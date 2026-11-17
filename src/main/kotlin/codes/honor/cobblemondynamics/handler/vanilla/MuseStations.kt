package codes.honor.cobblemondynamics.handler.vanilla

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import java.util.concurrent.ConcurrentHashMap

/**
 * CD-MCH-06: the Muse's stations of other mods whose demand is not an open menu and whose mod gathers the table stats below the
 * station, such as CEI's Infuser. The compat package of the station's mod registers it behind that mod's guard, so this class names no
 * other mod.
 */
object MuseStations {
    /** The Muse's demand at a station, and how many blocks below the station its mod gathers the table stats. */
    class Station(val statsBelow: Int, val hasDemand: (ServerLevel, BlockPos) -> Boolean)

    private val stations = ConcurrentHashMap<ResourceLocation, Station>()

    fun register(block: ResourceLocation, station: Station) {
        stations[block] = station
    }

    /** The station of [block], or null for a table whose demand is its open menu. */
    fun of(block: ResourceLocation): Station? = stations[block]

    /** The positions above [gathered] where a station would stand whose mod gathers the stats at [gathered], nearest first. */
    fun above(gathered: BlockPos): List<BlockPos> =
        stations.values.map { it.statsBelow }.filter { it > 0 }.distinct().sorted().map { gathered.above(it) }

    /** Whether [block] is a station whose mod gathers the table stats [distance] blocks below it. */
    fun gathersBelow(block: ResourceLocation, distance: Int): Boolean = stations[block]?.statsBelow == distance
}
