package codes.honor.cobblemondynamics.mixin.compat.easymagic;

import codes.honor.cobblemondynamics.handler.vanilla.MuseBonus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds a Working Muse's bookshelves to the power of Easy Magic's table (CD-FIX-05). Its menu extends the vanilla one, but counts the
 * power in a private method of its own and never makes the vanilla call that the menu mixin of the vanilla table changes. The offers
 * read this power, which vanilla's cost clamps at 15. Without the injection, the role gate turns this table off for the Muse
 * (CD-OPT-09).
 */
@Pseudo
@Mixin(targets = "fuzs.easymagic.world.inventory.ModEnchantmentMenu")
public abstract class ModEnchantmentMenuMixin {
    @Inject(
            method = "getEnchantingPower(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"),
            cancellable = true)
    private void cobblemon_dynamics$musePower(Level level, BlockPos table, CallbackInfoReturnable<Integer> cir) {
        int bonus = MuseBonus.of(level, table);
        if (bonus > 0) cir.setReturnValue(MuseBonus.capped(cir.getReturnValue(), bonus));
    }
}
