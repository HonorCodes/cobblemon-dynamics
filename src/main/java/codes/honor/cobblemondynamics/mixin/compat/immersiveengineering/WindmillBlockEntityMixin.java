package codes.honor.cobblemondynamics.mixin.compat.immersiveengineering;

import codes.honor.cobblemondynamics.compat.immersiveengineering.IeHooks;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Puts a Working Tailwind worker's weather factor in place of IE's own (CD-AIR-05). The server sends this speed to the dynamo on
 * every tick, and the client turns the windmill on screen by it (CD-AIR-06). Without the injection, the role gate turns Tailwind on
 * the IE windmill off with one warning (CD-OPT-09).
 */
@Pseudo
@Mixin(targets = "blusunrize.immersiveengineering.common.blocks.wooden.WindmillBlockEntity")
public abstract class WindmillBlockEntityMixin {
    @Inject(method = "getActualTurnSpeed()D", at = @At("RETURN"), cancellable = true)
    private void cobblemon_dynamics$weatherBonus(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(IeHooks.windmillSpeed((BlockEntity) (Object) this, cir.getReturnValue()));
    }
}
