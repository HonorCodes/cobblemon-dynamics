package codes.honor.cobblemondynamics.compat.pneumaticcraft;

/**
 * The flame gauge of an Air Compressor, whose {@code maxBurnTime} is private (CD-FUEL-08). Mixin refuses to load a class of the mixin
 * package, so this interface sits in the compat package. Its methods carry no handler mark, so the mixins line counts the gauge mixin as
 * applied once Mixin merged it, as it counts an accessor.
 */
public interface CompressorGauge {
    int dynamicsMaxBurnTime();

    void dynamicsSetMaxBurnTime(int maxBurnTime);
}
