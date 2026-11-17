package codes.honor.cobblemondynamics.mixin.compat.pneumaticcraft;

import codes.honor.cobblemondynamics.compat.pneumaticcraft.CompressorGauge;
import me.desht.pneumaticcraft.common.block.entity.compressor.AirCompressorBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Opens the private {@code maxBurnTime} and {@code airBuffer} of the Air Compressor and the Advanced Air Compressor, so the Stoker keeps
 * the flame gauge full and reads the air of the compressor's next tick (CD-FUEL-08). It shadows the fields rather than declare
 * accessors: the mixin plugin checks a shadow against the target and skips the mixin when a later PneumaticCraft renames a field, so the
 * gauge then stays as PneumaticCraft leaves it, the Stoker takes the buffer's bound, and the fuel still works.
 */
@Mixin(AirCompressorBlockEntity.class)
public abstract class AirCompressorMixin implements CompressorGauge {
    @Shadow
    private int maxBurnTime;

    @Shadow
    private float airBuffer;

    @Override
    public int dynamicsMaxBurnTime() {
        return maxBurnTime;
    }

    @Override
    public void dynamicsSetMaxBurnTime(int value) {
        maxBurnTime = value;
    }

    @Override
    public float dynamicsAirBuffer() {
        return airBuffer;
    }
}
