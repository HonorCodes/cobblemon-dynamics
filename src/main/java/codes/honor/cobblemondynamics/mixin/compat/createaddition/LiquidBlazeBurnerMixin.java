package codes.honor.cobblemondynamics.mixin.compat.createaddition;

import codes.honor.cobblemondynamics.compat.createaddition.LiquidBurnerHeat;
import com.mrh0.createaddition.blocks.liquid_blaze_burner.LiquidBlazeBurnerBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raises the heat of a Liquid Blaze Burner's own fuel to the heat of its Working Superheater, and never lowers it (CD-MCH-01). Only the
 * returned heat changes, so the fuel and its burn time stay the burner's own, and the burner's block state, which basins read, takes the
 * heat when the burner writes it. Without the injection the role gate turns the Superheater's liquid burner role off (CD-OPT-09).
 */
@Mixin(LiquidBlazeBurnerBlockEntity.class)
public abstract class LiquidBlazeBurnerMixin {
    @Shadow
    protected LiquidBlazeBurnerBlockEntity.FuelType activeFuel;

    @Shadow
    protected abstract BlazeBurnerBlock.HeatLevel getHeatLevelFromFuelType(LiquidBlazeBurnerBlockEntity.FuelType fuel);

    @Inject(method = "getHeatLevelFromFuelType", at = @At("RETURN"), cancellable = true)
    private void cobblemon_dynamics$heat(LiquidBlazeBurnerBlockEntity.FuelType fuel, CallbackInfoReturnable<BlazeBurnerBlock.HeatLevel> cir) {
        BlazeBurnerBlock.HeatLevel ours = LiquidBurnerHeat.levelFor((BlockEntity) (Object) this);
        if (ours != null && ours.isAtLeast(cir.getReturnValue())) cir.setReturnValue(ours);
    }

    /** A fueled burner writes its block state only when its fuel changes, so Seething heat that outlived its grant is dropped here. */
    @Inject(method = "tick()V", at = @At("HEAD"))
    private void cobblemon_dynamics$dropLapsedHeat(CallbackInfo ci) {
        LiquidBlazeBurnerBlockEntity burner = (LiquidBlazeBurnerBlockEntity) (Object) this;
        if (LiquidBurnerHeat.isStale(burner, burner.getHeatLevelFromBlock(), getHeatLevelFromFuelType(activeFuel))) burner.updateBlockState();
    }
}
