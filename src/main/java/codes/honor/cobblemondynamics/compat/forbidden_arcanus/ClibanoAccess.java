package codes.honor.cobblemondynamics.compat.forbidden_arcanus;

import net.minecraft.world.level.Level;

/**
 * What the Clibano's mixin adds to the Clibano, whose burn fields and smelt logic are private (CD-FUEL-10). Mixin refuses to load a
 * class of the mixin package, so this interface sits in the compat package.
 */
public interface ClibanoAccess {
    /** The Clibano's own check of its last tick: an input has a recipe that it can smelt. */
    boolean cobblemon_dynamics$smelts();

    /** Keeps the burn of a worked Clibano at the start of its tick, and shows the frame lit when the burn starts. */
    void cobblemon_dynamics$keepLit(Level level);
}
