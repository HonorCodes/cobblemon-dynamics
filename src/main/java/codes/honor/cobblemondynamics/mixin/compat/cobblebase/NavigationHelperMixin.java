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
 * Cobblebase's movers. Its pasture loop moves a Water-type on land into water within 20 blocks of the pasture, unsticks and wanders
 * Pokémon, and pulls them out of leaves. None of that touches a Work Mode pasture or its workers (CD-CMP-03).
 */
@Pseudo
@Mixin(targets = "notlown.cobblebase.core.NavigationHelper")
public abstract class NavigationHelperMixin {
    /** No water found, so the loop does not move the Pokémon. */
    @Inject(
            method = "findNearbyWaterCached(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;ILjava/util/UUID;J)Lnet/minecraft/core/BlockPos;",
            at = @At("HEAD"),
            cancellable = true)
    private void cobblemon_dynamics$waterTeleport(
            Level level, BlockPos pasture, int radius, UUID pokemonId, long time, CallbackInfoReturnable<BlockPos> cir) {
        if (CobblebaseCompat.skipsPasture(level, pasture)) cir.setReturnValue(null);
    }

    @Inject(
            method = "wanderNearOrigin(Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;Lnet/minecraft/core/BlockPos;I)V",
            at = @At("HEAD"),
            cancellable = true)
    private void cobblemon_dynamics$wander(PokemonEntity entity, BlockPos pasture, int radius, CallbackInfo ci) {
        if (CobblebaseCompat.skipsPasture(entity.level(), pasture)) ci.cancel();
    }

    @Inject(
            method = "checkAndUnstick(Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;Lnet/minecraft/core/BlockPos;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void cobblemon_dynamics$unstick(PokemonEntity entity, BlockPos pasture, CallbackInfo ci) {
        if (CobblebaseCompat.skipsPasture(entity.level(), pasture)) ci.cancel();
    }

    @Inject(method = "escapeLeaves(Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;)V", at = @At("HEAD"), cancellable = true)
    private void cobblemon_dynamics$leaves(PokemonEntity entity, CallbackInfo ci) {
        if (CobblebaseCompat.skipsEntity(entity)) ci.cancel();
    }
}
