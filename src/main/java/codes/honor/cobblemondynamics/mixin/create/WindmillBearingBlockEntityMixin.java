package codes.honor.cobblemondynamics.mixin.create;

import codes.honor.cobblemondynamics.handler.create.TailwindBonus;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.bearing.WindmillBearingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Adds a Working Tailwind worker's virtual sails to the speed of a Create windmill. A {@code @Redirect}, not MixinExtras'
 * {@code @ModifyExpressionValue}: MixinExtras injects that one after the config plugin's {@code postApply}, so {@code MixinState}
 * would never see the call, and the role gate would turn Tailwind off.
 */
@Mixin(WindmillBearingBlockEntity.class)
public abstract class WindmillBearingBlockEntityMixin {
    /**
     * The virtual sails count toward the speed, on both sides. Assembly reads the sail field itself, so they never help a windmill
     * assemble (CD-TLW-05), and Create's own clamp keeps the speed at 16 RPM or less (CD-BAL-04).
     */
    @Redirect(method = "getGeneratedSpeed()F", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/contraptions/bearing/BearingContraption;getSailBlocks()I"))
    private int cobblemon_dynamics$virtualSails(BearingContraption contraption) {
        return TailwindBonus.totalSails((WindmillBearingBlockEntity) (Object) this, contraption.getSailBlocks());
    }
}
