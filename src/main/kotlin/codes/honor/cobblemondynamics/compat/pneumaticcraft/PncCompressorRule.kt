package codes.honor.cobblemondynamics.compat.pneumaticcraft

/** A compressor's public `burnTime` and its private `maxBurnTime`, which its flame gauge divides by. */
data class CompressorBurn(val time: Int, val max: Int)

/**
 * The Stoker's rules on PneumaticCraft's Air Compressor and Advanced Air Compressor, over plain facts, so that unit tests load them with
 * no PneumaticCraft class. Each tick the compressor takes a fuel item only while `burnTime` is below its fuel use, then burns its fuel
 * use and makes air. Its fuel use is (int)(base production × the usage multiplier of its Speed Upgrades ÷ 10) burn ticks per tick
 * (RES12). The engine works after the block entities tick, so a worker that adds the base rate each tick keeps a compressor with no
 * Speed Upgrade from its fuel slot (CD-FUEL-13), and Speed Upgrades still cost fuel (CD-FUEL-08).
 */
object PncCompressorRule {
    /** CD-FUEL-08: the fuel use of a compressor with no Speed Upgrade: 1 burn tick per tick at base production 10, 5 at 50. */
    fun baseBurnRate(baseProduction: Int): Int = (baseProduction / 10).coerceAtLeast(0)

    /** CD-FUEL-08: below the danger pressure, while the compressor's own redstone setting lets it take fuel. */
    fun hasDemand(pressure: Float, dangerPressure: Float, runs: Boolean): Boolean = runs && pressure < dangerPressure

    /**
     * One worked tick: [rate] more burn ticks. The gauge's maximum grows with the burn, so the gauge never shows more than full, and it
     * is full while only the worker's burn is left. A fuel item that still burns keeps its own maximum.
     */
    fun worked(burn: CompressorBurn, rate: Int): CompressorBurn {
        if (rate <= 0) return burn
        val time = burn.time + rate
        val max = if (burn.time < rate || time > burn.max) time else burn.max
        return CompressorBurn(time, max)
    }
}
