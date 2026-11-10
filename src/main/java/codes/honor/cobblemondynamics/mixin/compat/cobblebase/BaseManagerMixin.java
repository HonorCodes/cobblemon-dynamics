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

/** Cobblebase's jobs and its return-to-pasture teleport run from `tickPokemon`, which skips Work Mode pastures (CD-CMP-03). */
@Pseudo
@Mixin(targets = "notlown.cobblebase.core.BaseManager")
public abstract class BaseManagerMixin {
    @Inject(
            method = "tickPokemon(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void cobblemon_dynamics$jobs(Level level, BlockPos pasture, PokemonEntity entity, CallbackInfo ci) {
        if (CobblebaseCompat.skipsPasture(level, pasture)) ci.cancel();
    }
}
