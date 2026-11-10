package codes.honor.cobblemondynamics.mixin.compat.cobblebase;

import codes.honor.cobblemondynamics.compat.CobblebaseCompat;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cobblebase's idle behaviour: sitting, sleeping, socializing and wandering. Its sleep lock, a `PokemonEntity.tick` hook, erases the
 * Brain's walk target of every Pokémon that it counts as asleep, so a worker never counts as asleep or resting (CD-CMP-03).
 */
@Pseudo
@Mixin(targets = "notlown.cobblebase.core.AmbientBehavior")
public abstract class AmbientBehaviorMixin {
    @Inject(method = "isSleeping(Ljava/util/UUID;)Z", at = @At("HEAD"), cancellable = true)
    private void cobblemon_dynamics$sleepLock(UUID pokemonId, CallbackInfoReturnable<Boolean> cir) {
        if (CobblebaseCompat.skipsPokemon(pokemonId)) cir.setReturnValue(false);
    }

    @Inject(method = "shouldPreventMovement(Ljava/util/UUID;)Z", at = @At("HEAD"), cancellable = true)
    private void cobblemon_dynamics$restLock(UUID pokemonId, CallbackInfoReturnable<Boolean> cir) {
        if (CobblebaseCompat.skipsPokemon(pokemonId)) cir.setReturnValue(false);
    }

    /** True means "handled", so Cobblebase starts no idle behaviour and no wander. */
    @Inject(
            method = "tickIdle(Lnet/minecraft/world/level/Level;Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void cobblemon_dynamics$idle(Level level, PokemonEntity entity, BlockPos pasture, CallbackInfoReturnable<Boolean> cir) {
        if (CobblebaseCompat.skipsPasture(level, pasture)) cir.setReturnValue(true);
    }

    @Inject(
            method = "tickSpecialAnimations(Lnet/minecraft/world/level/Level;Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void cobblemon_dynamics$animations(Level level, PokemonEntity entity, CallbackInfo ci) {
        if (CobblebaseCompat.skipsEntity(entity)) ci.cancel();
    }
}
