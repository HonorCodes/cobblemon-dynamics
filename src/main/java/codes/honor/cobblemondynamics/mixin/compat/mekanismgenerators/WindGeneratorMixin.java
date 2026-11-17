package codes.honor.cobblemondynamics.mixin.compat.mekanismgenerators;

import codes.honor.cobblemondynamics.compat.mekanismgenerators.WindGeneratorHandler;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raises a Wind Generator's multiplier while a Tailwind worker works it (CD-AIR-02). Mekanism stores the result every 20 ticks
 * and makes {@code windGenerationMin} times it J per tick. A multiplier of 0, with no sky or a fluid above the generator, stays 0.
 * Without the injection, the role gate turns Tailwind on Mekanism off with one warning (CD-OPT-09).
 */
@Pseudo
@Mixin(targets = "mekanism.generators.common.tile.TileEntityWindGenerator")
public abstract class WindGeneratorMixin {
    @Inject(method = "getMultiplier()D", at = @At("RETURN"), cancellable = true)
    private void cobblemon_dynamics$tailwind(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(WindGeneratorHandler.multiplier((BlockEntity) (Object) this, cir.getReturnValue()));
    }
}
