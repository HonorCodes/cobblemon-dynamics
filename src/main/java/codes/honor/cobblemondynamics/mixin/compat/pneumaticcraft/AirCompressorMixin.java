package codes.honor.cobblemondynamics.mixin.compat.pneumaticcraft;

import codes.honor.cobblemondynamics.compat.pneumaticcraft.CompressorGauge;
import me.desht.pneumaticcraft.common.block.entity.compressor.AirCompressorBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Opens the private {@code maxBurnTime} of the Air Compressor and the Advanced Air Compressor, so the Stoker keeps the flame gauge full
 * (CD-FUEL-08). It shadows the field rather than declare an accessor: the mixin plugin checks a shadow against the target and skips the
 * mixin when a later PneumaticCraft renames the field, so the gauge then stays as PneumaticCraft leaves it and the fuel still works.
 */
@Mixin(AirCompressorBlockEntity.class)
public abstract class AirCompressorMixin implements CompressorGauge {
    @Shadow
    private int maxBurnTime;

    @Override
    public int dynamicsMaxBurnTime() {
        return maxBurnTime;
    }

    @Override
    public void dynamicsSetMaxBurnTime(int value) {
        maxBurnTime = value;
    }
}
