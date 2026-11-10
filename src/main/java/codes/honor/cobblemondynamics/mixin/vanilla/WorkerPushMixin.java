package codes.honor.cobblemondynamics.mixin.vanilla;

import codes.honor.cobblemondynamics.engine.WorkerPush;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips the push between two workers that the engine directs. {@code LivingEntity.doPush} calls the pushed entity's
 * {@code push(Entity)}, which moves both entities, so a skip here covers both directions. Any other pair pushes as usual.
 */
@Mixin(LivingEntity.class)
public abstract class WorkerPushMixin {
    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void cobblemon_dynamics$workersPassThrough(Entity other, CallbackInfo ci) {
        if (WorkerPush.ignores((LivingEntity) (Object) this, other)) ci.cancel();
    }
}
