package codes.honor.cobblemondynamics.handler.capability

import net.neoforged.neoforge.energy.IEnergyStorage

/**
 * A target's energy store as the Generator sees it. NeoForge 21.9 replaces `IEnergyStorage` with its transfer API, so only [of]
 * names it.
 */
internal interface EnergyPort {
    /** How much of [fe] the target would take, with no change to it. */
    fun simulate(fe: Int): Int

    /** Gives the target up to [fe] and returns what it took. */
    fun push(fe: Int): Int

    companion object {
        fun of(storage: IEnergyStorage): EnergyPort = object : EnergyPort {
            override fun simulate(fe: Int): Int = storage.receiveEnergy(fe, true)

            override fun push(fe: Int): Int = storage.receiveEnergy(fe, false)
        }
    }
}

/** One Generator tick on an [EnergyPort]. */
internal object FeTick {
    /** CD-GEN-06: a simulated insert of 1 FE succeeds. A worker that gives no FE has no demand either. */
    fun hasDemand(port: EnergyPort, fePerTick: Int): Boolean = fePerTick > 0 && port.simulate(1) == 1

    /** CD-GEN-05: one offer of the rate. What the target refuses is not kept, so no tick offers more than the rate. */
    fun work(port: EnergyPort, fePerTick: Int): Int = if (fePerTick > 0) port.push(fePerTick) else 0
}
