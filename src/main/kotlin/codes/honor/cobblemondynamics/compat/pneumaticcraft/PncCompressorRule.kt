package codes.honor.cobblemondynamics.compat.pneumaticcraft

import kotlin.math.ceil

/** A compressor's public `burnTime` and its private `maxBurnTime`, which its flame gauge divides by. */
data class CompressorBurn(val time: Int, val max: Int)

/**
 * A compressor's air: the stored air and the volume of its air handler, its public `airPerTick`, and its private `airBuffer`, the
 * part of a mL that its last burn left.
 */
data class CompressorAir(val stored: Int, val volume: Int, val perTick: Float, val buffer: Float)

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

    /** The buffer holds less than 1 mL after each tick of the compressor, so 1 bounds it where the mixin that reads it did not apply. */
    const val UNKNOWN_BUFFER = 1f

    /**
     * CD-FUEL-08: while the compressor's own redstone setting lets it take fuel, and the air of the next tick keeps it below
     * [dangerPressure], the lowest danger pressure of the compressor and the machines connected to it. A burn adds `airPerTick` to
     * `airBuffer` and then the whole mL of the buffer to the air (javap, 8.2.24), so the burn of one worked tick adds at most
     * ceil(airBuffer + airPerTick) mL in the next. PneumaticCraft divides the air by the volume as floats, and so does this check.
     */
    fun hasDemand(air: CompressorAir, dangerPressure: Float, runs: Boolean): Boolean {
        if (!runs || air.volume <= 0) return false
        val next = air.stored.toLong() + ceil(air.buffer.toDouble() + air.perTick.toDouble()).toLong()
        return next.toFloat() / air.volume.toFloat() < dangerPressure
    }

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
