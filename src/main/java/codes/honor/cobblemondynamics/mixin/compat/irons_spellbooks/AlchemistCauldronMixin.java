package codes.honor.cobblemondynamics.mixin.compat.irons_spellbooks;

import codes.honor.cobblemondynamics.compat.irons_spellbooks.CauldronBrewing;
import codes.honor.cobblemondynamics.compat.irons_spellbooks.CauldronRule;
import io.redspace.ironsspellbooks.block.alchemist_cauldron.AlchemistCauldronTile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Brews faster in a worked Alchemist Cauldron (CD-MCH-07). At the end of the cauldron's own tick, while a Poison-type's grant holds, each
 * running cook time takes its extra steps, never past its end, so the item melts on the cauldron's own next step. Without the injection
 * the role gate turns the Brewer's cauldron role off (CD-OPT-09).
 */
@Mixin(AlchemistCauldronTile.class)
public abstract class AlchemistCauldronMixin implements CauldronBrewing {
    @Shadow
    @Final
    private int[] cooktimes;

    @Unique
    private long cobblemon_dynamics$grantedUntil = Long.MIN_VALUE;

    @Unique
    private int cobblemon_dynamics$steps;

    @Inject(method = "serverTick", at = @At("TAIL"))
    private static void cobblemon_dynamics$afterTick(Level level, BlockPos pos, BlockState state, AlchemistCauldronTile cauldron, CallbackInfo ci) {
        ((CauldronBrewing) (Object) cauldron).cobblemon_dynamics$brew(level.getGameTime());
    }

    @Override
    public boolean cobblemon_dynamics$cooking() {
        return CauldronRule.INSTANCE.cooking(cooktimes);
    }

    @Override
    public void cobblemon_dynamics$grant(long until, int steps) {
        cobblemon_dynamics$grantedUntil = until;
        cobblemon_dynamics$steps = steps;
    }

    @Override
    public void cobblemon_dynamics$brew(long now) {
        if (now <= cobblemon_dynamics$grantedUntil) CauldronRule.INSTANCE.advance(cooktimes, cobblemon_dynamics$steps);
    }
}
