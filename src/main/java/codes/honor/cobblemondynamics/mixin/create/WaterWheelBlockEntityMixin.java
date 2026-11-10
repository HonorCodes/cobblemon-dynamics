package codes.honor.cobblemondynamics.mixin.create;

import codes.honor.cobblemondynamics.handler.create.RealFlowAccess;
import codes.honor.cobblemondynamics.handler.create.VirtualFlow;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Gives a dry wheel the flow of a Working Wheel Driver, so Create turns it at its own speed and capacity (CD-WHL-02). The large
 * wheel inherits the method. It applies on both sides: clients recompute the flow from the synced drive behaviour (CD-WHL-09).
 */
@Mixin(WaterWheelBlockEntity.class)
public abstract class WaterWheelBlockEntityMixin implements RealFlowAccess {
    @Unique
    private int cobblemon_dynamics$realScore;

    @ModifyArg(method = "determineAndApplyFlowScore()V", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/kinetics/waterwheel/WaterWheelBlockEntity;setFlowScoreAndUpdate(I)V"))
    private int cobblemon_dynamics$virtualFlow(int realScore) {
        cobblemon_dynamics$realScore = realScore;
        // Real fluid wins, so a worker never reverses a wheel that water already turns (CD-WHL-03).
        return realScore != 0 ? realScore : VirtualFlow.sign((WaterWheelBlockEntity) (Object) this);
    }

    @Override
    public int cobblemon_dynamics$realFlowScore() {
        return cobblemon_dynamics$realScore;
    }
}
