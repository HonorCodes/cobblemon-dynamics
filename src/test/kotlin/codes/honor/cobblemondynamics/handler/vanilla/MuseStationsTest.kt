package codes.honor.cobblemondynamics.handler.vanilla

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MuseStationsTest {
    /** A station whose mod gathers the table stats 2 blocks below it. CEI's Infuser gathers them 1 block below. */
    private val station = ResourceLocation.fromNamespaceAndPath("cobblemon_dynamics", "test_station")

    @Test
    fun `CD-MCH-06 a gather below a station finds the station above it, and only a station that gathers at that distance`() {
        MuseStations.register(station, MuseStations.Station(statsBelow = 2) { _, _ -> true })
        val gathered = BlockPos(10, 64, -3)
        assertTrue(BlockPos(10, 66, -3) in MuseStations.above(gathered), "the gather does not look 2 blocks up")
        assertFalse(gathered in MuseStations.above(gathered), "the gathered position is the table's own, which the Muse claims itself")
        assertTrue(MuseStations.gathersBelow(station, 2))
        assertFalse(MuseStations.gathersBelow(station, 1), "a station gathers at another distance")
        assertFalse(MuseStations.gathersBelow(ResourceLocation.withDefaultNamespace("enchanting_table"), 2), "a table is a station")
    }
}
