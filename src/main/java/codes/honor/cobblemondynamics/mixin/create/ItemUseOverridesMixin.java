package codes.honor.cobblemondynamics.mixin.create;

import codes.honor.cobblemondynamics.leash.LeashInteractions;
import com.simibubi.create.foundation.block.ItemUseOverrides;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Create turns a hand crank on every right-click whose item is not its wrench, and cancels the click before the item gets it. The
 * Work Leash is exempt the same way as the wrench, so the leash's own click binds and flips cranks (CD-CRK-06), and only after a
 * protection mod that cancels the click has had its say (CD-SEC-11).
 */
@Mixin(ItemUseOverrides.class)
public abstract class ItemUseOverridesMixin {
    @Inject(method = "onBlockActivated", at = @At("HEAD"), cancellable = true)
    private static void cobblemon_dynamics$leashFirst(PlayerInteractEvent.RightClickBlock event, CallbackInfo ci) {
        if (LeashInteractions.isWorkLeash(event.getItemStack())) ci.cancel();
    }
}
