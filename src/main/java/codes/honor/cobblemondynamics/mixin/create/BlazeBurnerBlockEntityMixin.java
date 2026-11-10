package codes.honor.cobblemondynamics.mixin.create;

import codes.honor.cobblemondynamics.handler.create.PokemonHeat;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raises the heat that a Blaze Burner's own fuel gives to the heat of its Working worker, and never lowers it. Only the returned value
 * changes, so the fuel and its burn time stay as they are (CD-BRN-04), and Create's own tick writes the result into the block state
 * that basins and boilers read (CD-BRN-05, CD-SUP-03).
 */
@Mixin(BlazeBurnerBlockEntity.class)
public abstract class BlazeBurnerBlockEntityMixin {
    @Inject(method = "getHeatLevel()Lcom/simibubi/create/content/processing/burner/BlazeBurnerBlock$HeatLevel;",
            at = @At("RETURN"), cancellable = true)
    private void cobblemon_dynamics$heat(CallbackInfoReturnable<BlazeBurnerBlock.HeatLevel> cir) {
        BlazeBurnerBlock.HeatLevel ours = PokemonHeat.levelFor((BlazeBurnerBlockEntity) (Object) this);
        if (ours != null && ours.isAtLeast(cir.getReturnValue())) cir.setReturnValue(ours);
    }
}
