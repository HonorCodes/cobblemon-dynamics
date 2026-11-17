package codes.honor.cobblemondynamics.mixin.compat.forbidden_arcanus;

import codes.honor.cobblemondynamics.compat.forbidden_arcanus.ClibanoAccess;
import codes.honor.cobblemondynamics.compat.forbidden_arcanus.ClibanoBurn;
import codes.honor.cobblemondynamics.compat.forbidden_arcanus.ClibanoRule;
import codes.honor.cobblemondynamics.compat.forbidden_arcanus.ClibanoStoking;
import com.stal111.forbidden_arcanus.common.block.entity.clibano.ClibanoMainBlockEntity;
import com.stal111.forbidden_arcanus.common.block.entity.clibano.logic.ClibanoSmeltLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Burns a worked Clibano with no fuel item (CD-FUEL-10). At the start of the Clibano's own tick, while a Fire-type works it, the burn
 * time is kept at 2 or more, so the tick finds the Clibano lit and leaves its fuel slot alone (CD-FUEL-13). The fire type stays the
 * Clibano's own. Without the injection the role gate turns the Stoker's Clibano role off (CD-OPT-09).
 */
@Mixin(ClibanoMainBlockEntity.class)
public abstract class ClibanoMixin implements ClibanoAccess {
    @Shadow
    private int burnTime;

    @Shadow
    private int burnDuration;

    @Shadow
    private boolean wasLit;

    @Shadow
    private ClibanoSmeltLogic logic;

    @Shadow
    private void updateAppearance(Level level) {
    }

    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void cobblemon_dynamics$stoke(Level level, BlockPos pos, BlockState state, ClibanoMainBlockEntity clibano, CallbackInfo ci) {
        if (ClibanoStoking.isGranted(clibano)) ((ClibanoAccess) (Object) clibano).cobblemon_dynamics$keepLit(level);
    }

    @Override
    public boolean cobblemon_dynamics$smelts() {
        return logic.canSmelt();
    }

    @Override
    public void cobblemon_dynamics$keepLit(Level level) {
        ClibanoBurn kept = ClibanoRule.INSTANCE.keep(new ClibanoBurn(burnTime, burnDuration));
        if (kept.getTime() == burnTime) return;
        // The Clibano shows its frame lit only when it burns a fuel item from a cold start, so a worker's start shows it here.
        boolean shownOut = burnTime <= 0 && !wasLit;
        burnTime = kept.getTime();
        burnDuration = kept.getDuration();
        if (shownOut) updateAppearance(level);
    }
}
