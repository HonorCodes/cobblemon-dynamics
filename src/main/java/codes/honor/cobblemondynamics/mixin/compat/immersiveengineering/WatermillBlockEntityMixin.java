package codes.honor.cobblemondynamics.mixin.compat.immersiveengineering;

import codes.honor.cobblemondynamics.compat.immersiveengineering.IeHooks;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives a dry watermill the torque of its Working Wheel Driver (CD-KIN-02). IE reads each wheel's torque here when it sums a row of
 * wheels on a Kinetic Dynamo, and it turns the wheel at the speed of that sum. Without the injection, the role gate turns the IE
 * Wheel Driver off with one warning (CD-OPT-09).
 */
@Pseudo
@Mixin(targets = "blusunrize.immersiveengineering.common.blocks.wooden.WatermillBlockEntity")
public abstract class WatermillBlockEntityMixin {
    @Inject(method = "getIFScaledTorque()D", at = @At("RETURN"), cancellable = true)
    private void cobblemon_dynamics$virtualTorque(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(IeHooks.watermillTorque((BlockEntity) (Object) this, cir.getReturnValue()));
    }
}
