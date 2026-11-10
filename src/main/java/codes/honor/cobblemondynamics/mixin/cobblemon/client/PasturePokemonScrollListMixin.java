package codes.honor.cobblemondynamics.mixin.cobblemon.client;

import codes.honor.cobblemondynamics.client.PanelLayout;
import com.cobblemon.mod.common.client.gui.pasture.PasturePokemonScrollList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Shortens Cobblemon's pasture list so that it ends 2 pixels above the Work Mode button (CD-UI-01). */
@Mixin(PasturePokemonScrollList.class)
public abstract class PasturePokemonScrollListMixin {
    // correctSize() runs on every frame and sets the list to 70 × 120, so the new height holds for scrolling and clipping alike.
    @ModifyArg(
            method = "correctSize",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/client/gui/pasture/PasturePokemonScrollList;setRectangle(IIII)V"),
            index = 1)
    private int cobblemon_dynamics$listHeight(int height) {
        return PanelLayout.LIST_HEIGHT;
    }
}
