package codes.honor.cobblemondynamics.handler.capability

import net.minecraft.world.level.material.Fluids
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler

/**
 * A target's fluid store as the Pump sees it, which only ever offers water. NeoForge 21.9 replaces `IFluidHandler` with its transfer
 * API, so only [of] names it.
 */
internal interface FluidPort {
    /** How much of [mb] of water the target would take, with no change to it. */
    fun simulate(mb: Int): Int

    /** Fills the target with up to [mb] of water and returns what it took. */
    fun fill(mb: Int): Int

    /** Whether one of the target's tanks holds water already, as a full water tank does. */
    fun holdsWater(): Boolean

    companion object {
        fun of(handler: IFluidHandler): FluidPort = object : FluidPort {
            override fun simulate(mb: Int): Int = handler.fill(FluidStack(Fluids.WATER, mb), IFluidHandler.FluidAction.SIMULATE)

            override fun fill(mb: Int): Int = handler.fill(FluidStack(Fluids.WATER, mb), IFluidHandler.FluidAction.EXECUTE)

            override fun holdsWater(): Boolean = (0 until handler.tanks).any { handler.getFluidInTank(it).`is`(Fluids.WATER) }
        }
    }
}

/** One Pump tick on a [FluidPort]. */
internal object FluidTick {
    /**
     * CD-PMP-01: the target takes water now, or holds water already, so a full water tank can be bound. A tank of another fluid takes
     * none.
     */
    fun acceptsWater(port: FluidPort): Boolean = port.simulate(1) == 1 || port.holdsWater()

    /** CD-PMP-05: a simulated fill of 1 mB of water succeeds. A worker that gives no water has no demand either. */
    fun hasDemand(port: FluidPort, mbPerTick: Int): Boolean = mbPerTick > 0 && port.simulate(1) == 1

    /** CD-PMP-03: one offer of the rate. What the target refuses is not kept, so no tick offers more than the rate. */
    fun work(port: FluidPort, mbPerTick: Int): Int = if (mbPerTick > 0) port.fill(mbPerTick) else 0
}
