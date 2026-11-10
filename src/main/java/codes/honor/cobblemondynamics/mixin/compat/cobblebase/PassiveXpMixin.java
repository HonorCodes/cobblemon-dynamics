package codes.honor.cobblemondynamics.mixin.compat.cobblebase;

import codes.honor.cobblemondynamics.compat.CobblebaseCompat;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The passive experience that Cobblebase's pasture loop gives is part of the loop that a Work Mode pasture skips (CD-CMP-03). */
@Pseudo
@Mixin(targets = "notlown.cobblebase.core.PassiveXp")
public abstract class PassiveXpMixin {
    @Inject(
            method = "tick(Lnet/minecraft/world/level/Level;Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;Lnet/minecraft/core/BlockPos;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void cobblemon_dynamics$passiveXp(Level level, PokemonEntity entity, BlockPos pasture, CallbackInfo ci) {
        if (CobblebaseCompat.skipsPasture(level, pasture)) ci.cancel();
    }
}
