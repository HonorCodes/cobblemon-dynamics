package codes.honor.cobblemondynamics.mixin.compat.apothic;

import codes.honor.cobblemondynamics.handler.vanilla.MuseBonus;
import dev.shadowsoffire.apothic_enchanting.table.EnchantmentTableStats;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds a Working Muse's Quanta to the stats that Apothic Enchanting gathers around a table (CD-MUS-07). Its table menu computes the
 * offers from these stats, never from vanilla's bookshelf count, and sends them to the player's screen, so the screen shows the Quanta
 * that the offers use. The record's constructor clamps Quanta to 100. Without the injection, MixinGate turns the Muse off (CD-MUS-06).
 */
@Pseudo
@Mixin(targets = "dev.shadowsoffire.apothic_enchanting.table.EnchantmentTableStats")
public abstract class EnchantmentTableStatsMixin {
    @Inject(
            method = "gatherStats(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Ldev/shadowsoffire/apothic_enchanting/table/EnchantmentTableStats;",
            at = @At("RETURN"),
            cancellable = true)
    private static void cobblemon_dynamics$museQuanta(LevelReader level, BlockPos table, CallbackInfoReturnable<EnchantmentTableStats> cir) {
        float quanta = MuseBonus.quantaOf(level, table);
        if (quanta <= 0) return;
        EnchantmentTableStats stats = cir.getReturnValue();
        cir.setReturnValue(new EnchantmentTableStats(stats.eterna(), stats.quanta() + quanta, stats.arcana(), stats.clues(),
                stats.blacklist(), stats.treasure(), stats.stable()));
    }
}
