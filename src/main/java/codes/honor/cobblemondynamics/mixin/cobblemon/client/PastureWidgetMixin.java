package codes.honor.cobblemondynamics.mixin.cobblemon.client;

import codes.honor.cobblemondynamics.client.PanelBars;
import com.cobblemon.mod.common.client.gui.pasture.PastureWidget;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the Work Stamina bars and state icons over the pasture list, after Cobblemon has drawn the panel (CD-UI-04). */
@Mixin(PastureWidget.class)
public abstract class PastureWidgetMixin {
    @Inject(method = "renderWidget(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("TAIL"))
    private void cobblemon_dynamics$drawBars(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        PanelBars.INSTANCE.render((PastureWidget) (Object) this, graphics, mouseX, mouseY);
    }
}
