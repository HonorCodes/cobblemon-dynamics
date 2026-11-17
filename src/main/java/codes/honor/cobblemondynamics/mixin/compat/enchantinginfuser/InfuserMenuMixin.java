package codes.honor.cobblemondynamics.mixin.compat.enchantinginfuser;

import codes.honor.cobblemondynamics.MuseStationMenu;
import codes.honor.cobblemondynamics.handler.vanilla.MuseBonus;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
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
 * the Muse's open-menu check, and takes a Muse's start or stop without the reset of a new item. With Apothic Enchanting the Muse gives
 * no bookshelves, so this adds nothing. Without the injection, the role gate turns both infusers off for the Muse (CD-OPT-09).
 */
@Pseudo
@Mixin(targets = "fuzs.enchantinginfuser.world.inventory.InfuserMenu")
public abstract class InfuserMenuMixin implements MuseStationMenu {
    @Shadow
    @Final
    private ContainerLevelAccess levelAccess;

    @Shadow
    @Final
    private DataSlot enchantmentPower;

    @Shadow
    @Final
    private DataSlot enchantingCost;

    /** The levels that the player chose, which the menu applies. */
    @Shadow
    private Object2IntMap<Holder<Enchantment>> enchantmentLevels;

    /** The highest level of each enchantment that the power offers. */
    @Shadow
    private Object2IntMap<Holder<Enchantment>> availableEnchantmentLevels;

    @Shadow
    private boolean markedDirty;

    @Shadow
    public abstract int getEnchantmentPowerLimit();

    @Shadow
    public abstract ItemStack getEnchantableStack();

    @Shadow
    public abstract ItemEnchantments getItemEnchantments();

    @Shadow
    private int getAvailablePower(Level level, BlockPos pos) {
        throw new AssertionError();
    }

    @Shadow
    private boolean mayEnchantStack(ItemStack stack) {
        throw new AssertionError();
    }

    @Shadow
    private void initializeEnchantmentMaps(Level level) {
        throw new AssertionError();
    }

    @Shadow
    private ItemEnchantments getOriginalEnchantments() {
        throw new AssertionError();
    }

    @Shadow
    private int calculateEnchantingCost() {
        throw new AssertionError();
    }

    @Shadow
    private void sendEnchantments(Optional<ItemEnchantments> enchantments) {
        throw new AssertionError();
    }

    @Override
    public ContainerLevelAccess cobblemon_dynamics$station() {
        return levelAccess;
    }

    /**
     * The menu's update for a new item resets the chosen levels to the item's own. This sets the power alone, offers the levels of the
     * new power, and lowers each chosen level above them (CD-FIX-06). The infuser's own message then gives the client the levels and
     * makes it compute its offers again, so the client reads the new power first.
     */
    @Override
    public void cobblemon_dynamics$refreshPower() {
        levelAccess.execute((level, pos) -> {
            int power = getAvailablePower(level, pos);
            if (power == enchantmentPower.get()) return;
            enchantmentPower.set(power);
            // With no item to enchant, the offers are empty, and the next broadcast sends the power.
            if (!mayEnchantStack(getEnchantableStack())) return;
            initializeEnchantmentMaps(level);
            ItemEnchantments own = getOriginalEnchantments();
            Object2IntMap<Holder<Enchantment>> lowered = new Object2IntOpenHashMap<>();
            for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantmentLevels.object2IntEntrySet()) {
                Holder<Enchantment> enchantment = entry.getKey();
                int chosen = entry.getIntValue();
                int kept = MuseBonus.clampedLevel(chosen, availableEnchantmentLevels.getInt(enchantment), own.getLevel(enchantment));
                if (kept != chosen) lowered.put(enchantment, kept);
            }
            if (!lowered.isEmpty()) enchantmentLevels.putAll(lowered);
            AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
            menu.broadcastChanges();
            sendEnchantments(Optional.of(getItemEnchantments()));
            if (lowered.isEmpty()) return;
            // After the message, so that the client's enchant button reads the lowered levels.
            markedDirty = !getItemEnchantments().equals(own);
            enchantingCost.set(calculateEnchantingCost());
            menu.broadcastChanges();
        });
    }

    @Inject(
            method = "getAvailablePower(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"),
            cancellable = true)
    private void cobblemon_dynamics$musePower(Level level, BlockPos table, CallbackInfoReturnable<Integer> cir) {
        int bonus = MuseBonus.of(level, table);
        if (bonus > 0) cir.setReturnValue(MuseBonus.capped(cir.getReturnValue(), bonus, getEnchantmentPowerLimit()));
    }

    /**
     * The infuser's message gives the client its levels as unchanged ones, and the client sends an enchant click only for a change. So
     * after a refresh, the client counts levels that differ from the item's own as a change again (CD-FIX-06).
     */
    @Inject(method = "canEnchant(Lnet/minecraft/world/entity/player/Player;)Z", at = @At("HEAD"))
    private void cobblemon_dynamics$keepChoice(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (player.level().isClientSide() && !markedDirty && !getEnchantableStack().isEmpty()
                && !MuseBonus.sameLevels(getItemEnchantments(), getOriginalEnchantments())) {
            markedDirty = true;
        }
    }
}
