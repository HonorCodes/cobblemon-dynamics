package codes.honor.cobblemondynamics.mixin.vanilla;

import codes.honor.cobblemondynamics.handler.vanilla.MuseBonus;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Adds a Working Muse's bookshelves to the count that the menu passes to {@code EnchantmentHelper.getEnchantmentCost} for each of its
 * three offers (CD-MUS-03, CD-MUS-04). The menu seeds its random source before the first offer, and each call draws the same two
 * values whatever the count, so the offers equal those of a table with that many shelves. The lambda's name is the compiler's. A game
 * whose menu has no such call gets no injection, and MixinGate turns the Muse off (CD-MUS-06).
 */
@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuMixin {
    @Shadow
    @Final
    private ContainerLevelAccess access;

    @ModifyArg(
            method = "lambda$slotsChanged$0(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getEnchantmentCost(Lnet/minecraft/util/RandomSource;IILnet/minecraft/world/item/ItemStack;)I"),
            index = 2)
    private int cobblemon_dynamics$shelves(int bookshelves) {
        return MuseBonus.capped(bookshelves, access.evaluate(MuseBonus::of, 0));
    }
}
