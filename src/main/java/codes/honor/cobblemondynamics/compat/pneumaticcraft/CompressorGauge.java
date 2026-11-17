package codes.honor.cobblemondynamics.compat.pneumaticcraft;

/**
 * The flame gauge and the air buffer of an Air Compressor, whose {@code maxBurnTime} and {@code airBuffer} are private (CD-FUEL-08).
 * Mixin refuses to load a class of the mixin package, so this interface sits in the compat package. Its methods carry no handler mark,
 * so the mixins line counts the gauge mixin as applied once Mixin merged it, as it counts an accessor.
 */
public interface CompressorGauge {
    int dynamicsMaxBurnTime();

    void dynamicsSetMaxBurnTime(int maxBurnTime);

    /** The part of a mL that the compressor's last burn left, which its next burn adds to. */
    float dynamicsAirBuffer();
}
