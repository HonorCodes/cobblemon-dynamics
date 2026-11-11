package codes.honor.cobblemondynamics.mixin.create;

import codes.honor.cobblemondynamics.handler.create.AirCurrentAccess;
import codes.honor.cobblemondynamics.handler.create.FanWorkers;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps a fan's worker out of Create's air current. The current lives in {@link AirCurrent}, not in the fan's block entity, and
 * ticks on both sides.
 */
@Mixin(AirCurrent.class)
public abstract class AirCurrentMixin implements AirCurrentAccess {
    @Shadow
    protected List<Entity> caughtEntities;

    @Shadow
    protected List<Pair<TransportedItemStackHandlerBehaviour, FanProcessingType>> affectedItemHandlers;

    /**
     * A fan's worker stands in its air current. Taking it out of the caught entities before Create walks the list stops the push,
     * the burning, the harm and the processing for that worker, on both sides, from the next tick of its mark (CD-FAN-12).
     */
    @Inject(method = "tickAffectedEntities(Lnet/minecraft/world/level/Level;)V", at = @At("HEAD"))
    private void cobblemon_dynamics$exemptWorkers(Level level, CallbackInfo ci) {
        caughtEntities.removeIf(FanWorkers::isExempt);
    }

    @Override
    public List<Entity> cobblemon_dynamics$caughtEntities() {
        return caughtEntities;
    }

    @Override
    public List<Pair<TransportedItemStackHandlerBehaviour, FanProcessingType>> cobblemon_dynamics$affectedHandlers() {
        return affectedItemHandlers;
    }
}
