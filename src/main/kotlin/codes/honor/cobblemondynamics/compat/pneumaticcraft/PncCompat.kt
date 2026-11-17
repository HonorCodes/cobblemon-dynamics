package codes.honor.cobblemondynamics.compat.pneumaticcraft

import codes.honor.cobblemondynamics.compat.AdapterNeeds
import codes.honor.cobblemondynamics.compat.CompatClasses
import codes.honor.cobblemondynamics.compat.CompatLines
import codes.honor.cobblemondynamics.handler.HandlerRegistry
import me.desht.pneumaticcraft.api.PNCCapabilities
import me.desht.pneumaticcraft.api.heat.IHeatExchangerLogic
import me.desht.pneumaticcraft.api.tileentity.IAirHandlerMachine
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent

/**
 * The PneumaticCraft: Repressurized integration: the Stoker on the compressors, the Heater on PneumaticCraft heat and Tailwind on
 * PneumaticCraft air (ADR-0020). The heat and the air go through PneumaticCraft's API capabilities. The mod calls [register] only while
 * PneumaticCraft is loaded.
 */
object PncCompat {
    private const val MOD = "pneumaticcraft"
    private const val COMPRESSOR = "me.desht.pneumaticcraft.common.block.entity.compressor.AirCompressorBlockEntity"

    fun register(modBus: IEventBus) {
        CompatLines.register(
            MOD,
            "pnc_compressor",
            AdapterNeeds(blocks = listOf(block("air_compressor"), block("advanced_air_compressor"))),
        )
        CompatLines.register(MOD, "pnc_heat")
        CompatLines.register(MOD, "pnc_air")
        HandlerRegistry.register(PncCompressorHandler)
        HandlerRegistry.register(PncHeatHandler)
        HandlerRegistry.register(PncAirHandler)
        // The gauge hook applies when the compressor class loads, and the mixins line reads it when the game has loaded (CD-CMP-08).
        modBus.addListener<FMLCommonSetupEvent> { it.enqueueWork(Runnable { CompatClasses.load(COMPRESSOR) }) }
    }

    /** The heat exchanger on [side] of [pos]. A position in an unloaded chunk has none. */
    fun heatAt(level: ServerLevel, pos: BlockPos, side: Direction): IHeatExchangerLogic? =
        if (level.isLoaded(pos)) level.getCapability(PNCCapabilities.HEAT_EXCHANGER_BLOCK, pos, side) else null

    /** The air handler on [side] of [pos]. A position in an unloaded chunk has none. */
    fun airAt(level: ServerLevel, pos: BlockPos, side: Direction): IAirHandlerMachine? =
        if (level.isLoaded(pos)) level.getCapability(PNCCapabilities.AIR_HANDLER_MACHINE, pos, side) else null

    private fun block(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD, path)
}
