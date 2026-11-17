package codes.honor.cobblemondynamics.xp

import net.minecraft.world.level.material.Fluid
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler

/**
 * NeoForge's fluid capability on the back and the bottom of a collector (CD-XPC-07): one tank of the chosen XP fluid that pipes drain
 * in whole points of XP (CD-XPC-09) and never fill (CD-XPC-10). [XpTank] holds the rules. A real drain calls [changed], which marks
 * the block entity changed and tells comparators.
 */
class XpFluidTank(private val tank: XpTank<Fluid>, private val changed: () -> Unit) : IFluidHandler {
    override fun getTanks(): Int = 1

    override fun getFluidInTank(index: Int): FluidStack {
        val fluid = tank.fluid()
        val amount = tank.amount()
        return if (index != 0 || fluid == null || amount <= 0) FluidStack.EMPTY else FluidStack(fluid, amount)
    }

    override fun getTankCapacity(index: Int): Int = if (index == 0) tank.capacity() else 0

    override fun isFluidValid(index: Int, stack: FluidStack): Boolean = !stack.isEmpty && tank.accepts(stack.fluid)

    override fun fill(resource: FluidStack, action: IFluidHandler.FluidAction): Int =
        if (resource.isEmpty) 0 else tank.fill(resource.fluid, resource.amount)

    override fun drain(resource: FluidStack, action: IFluidHandler.FluidAction): FluidStack {
        val fluid = tank.fluid() ?: return FluidStack.EMPTY
        if (resource.isEmpty || !resource.`is`(fluid)) return FluidStack.EMPTY
        return drain(resource.amount, action)
    }

    override fun drain(maxDrain: Int, action: IFluidHandler.FluidAction): FluidStack {
        val fluid = tank.fluid() ?: return FluidStack.EMPTY
        val mb = tank.drain(maxDrain, action.execute())
        if (mb <= 0) return FluidStack.EMPTY
        if (action.execute()) changed()
        return FluidStack(fluid, mb)
    }
}
