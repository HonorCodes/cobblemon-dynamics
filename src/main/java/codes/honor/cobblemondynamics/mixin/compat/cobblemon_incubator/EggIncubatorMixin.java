package codes.honor.cobblemondynamics.mixin.compat.cobblemon_incubator;

import codes.honor.cobblemondynamics.compat.cobblemon_incubator.IncubatorWarmth;
import com.nbp.cobblemon_incubator.blockentity.EggIncubatorBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Runs a worked Egg Incubator at the speed of one Speed Upgrade, where the incubator's config turns Speed Upgrades on (CD-FUEL-11). The
 * incubator reads this speed on each tick of an egg, and only its returned value changes, so an incubator with the upgrade gets nothing
 * more and the speed is its own again on the tick that the work ends (CD-FUEL-14). Without the injection the role gate turns the Stoker's
 * incubator role off (CD-OPT-09). The injection runs in the incubator's own tick, so {@code speedFor} reads the incubator's config only
 * for a warmed incubator, and behind the engine's guard (R88).
 */
@Mixin(EggIncubatorBlockEntity.class)
public abstract class EggIncubatorMixin {
    @Inject(method = "incubationSpeed()I", at = @At("RETURN"), cancellable = true)
    private void cobblemon_dynamics$warmth(CallbackInfoReturnable<Integer> cir) {
        int own = cir.getReturnValueI();
        int speed = IncubatorWarmth.speedFor((BlockEntity) (Object) this, own);
        if (speed != own) cir.setReturnValue(speed);
    }
}
