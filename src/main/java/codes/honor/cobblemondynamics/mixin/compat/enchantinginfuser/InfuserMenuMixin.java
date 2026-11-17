package codes.honor.cobblemondynamics.mixin.compat.enchantinginfuser;

import codes.honor.cobblemondynamics.MuseStationMenu;
import codes.honor.cobblemondynamics.handler.vanilla.MuseBonus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds a Working Muse's bookshelves to the power of both infusers of Enchanting Infuser, at the return of the private method that
 * counts it, within the menu's own limit of 15 (CD-FIX-06). The menu is no {@code EnchantmentMenu}, so it also names its block for
 * the Muse's open-menu check. With Apothic Enchanting the Muse gives no bookshelves, so this adds nothing. Without the injection, the
 * role gate turns both infusers off for the Muse (CD-OPT-09).
 */
@Pseudo
@Mixin(targets = "fuzs.enchantinginfuser.world.inventory.InfuserMenu")
public abstract class InfuserMenuMixin implements MuseStationMenu {
    @Shadow
    @Final
    private ContainerLevelAccess levelAccess;

    @Shadow
    public abstract int getEnchantmentPowerLimit();

    @Override
    public ContainerLevelAccess cobblemon_dynamics$station() {
        return levelAccess;
    }

    @Inject(
            method = "getAvailablePower(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"),
            cancellable = true)
    private void cobblemon_dynamics$musePower(Level level, BlockPos table, CallbackInfoReturnable<Integer> cir) {
        int bonus = MuseBonus.of(level, table);
        if (bonus > 0) cir.setReturnValue(MuseBonus.capped(cir.getReturnValue(), bonus, getEnchantmentPowerLimit()));
    }
}
