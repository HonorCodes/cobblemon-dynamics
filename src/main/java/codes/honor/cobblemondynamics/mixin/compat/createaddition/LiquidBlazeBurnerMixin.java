package codes.honor.cobblemondynamics.mixin.compat.createaddition;

import codes.honor.cobblemondynamics.compat.createaddition.LiquidBurnerAccess;
import codes.honor.cobblemondynamics.compat.createaddition.LiquidBurnerHeat;
import com.mrh0.createaddition.blocks.liquid_blaze_burner.LiquidBlazeBurnerBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raises the heat of a Liquid Blaze Burner's own fuel to the heat of its Working Superheater, and never lowers it (CD-MCH-01). Only the
 * returned heat changes, so the fuel and its burn time stay the burner's own, and the burner's block state, which basins read, takes the
 * heat when the burner writes it. Without the injection the role gate turns the Superheater's liquid burner role off (CD-OPT-09).
 *
 * <p>Both injections run in the burner's own tick, so they read the burner only through shadows (R88). The plugin skips this mixin when
 * the burner lacks one, and a later version then turns the role off instead of crashing the tick.
 */
@Mixin(LiquidBlazeBurnerBlockEntity.class)
public abstract class LiquidBlazeBurnerMixin implements LiquidBurnerAccess {
    @Shadow
    protected LiquidBlazeBurnerBlockEntity.FuelType activeFuel;

    /** Set while {@link #cobblemon_dynamics$ownHeat()} reads the heat of the burner's own fuel, which the heat injection then leaves. */
    @Unique
    private boolean cobblemon_dynamics$readingOwnHeat;

    @Shadow
    protected abstract BlazeBurnerBlock.HeatLevel getHeatLevelFromFuelType(LiquidBlazeBurnerBlockEntity.FuelType fuel);

    @Shadow
    public abstract BlazeBurnerBlock.HeatLevel getHeatLevelFromBlock();

    @Shadow
    public abstract void updateBlockState();

    @Shadow
    public abstract boolean isCreative();

    @Inject(method = "getHeatLevelFromFuelType", at = @At("RETURN"), cancellable = true)
    private void cobblemon_dynamics$heat(LiquidBlazeBurnerBlockEntity.FuelType fuel, CallbackInfoReturnable<BlazeBurnerBlock.HeatLevel> cir) {
        if (cobblemon_dynamics$readingOwnHeat) return;
        BlazeBurnerBlock.HeatLevel ours = LiquidBurnerHeat.levelFor((BlockEntity) (Object) this);
        if (ours != null && ours.isAtLeast(cir.getReturnValue())) cir.setReturnValue(ours);
    }

    /**
     * A fueled burner writes its block state only when its fuel changes, so Seething heat that outlived its grant is dropped here. A
     * client and a creative burner return before any other read.
     */
    @Inject(method = "tick()V", at = @At("HEAD"))
    private void cobblemon_dynamics$dropLapsedHeat(CallbackInfo ci) {
        BlockEntity burner = (BlockEntity) (Object) this;
        Level level = burner.getLevel();
        if (level == null || level.isClientSide || isCreative()) return;
        if (LiquidBurnerHeat.isStale(burner, getHeatLevelFromBlock(), cobblemon_dynamics$ownHeat())) updateBlockState();
    }

    @Override
    public BlazeBurnerBlock.HeatLevel cobblemon_dynamics$ownHeat() {
        cobblemon_dynamics$readingOwnHeat = true;
        try {
            return getHeatLevelFromFuelType(activeFuel);
        } finally {
            cobblemon_dynamics$readingOwnHeat = false;
        }
    }
}
