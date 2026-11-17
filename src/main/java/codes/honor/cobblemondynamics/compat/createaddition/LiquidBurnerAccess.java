package codes.honor.cobblemondynamics.compat.createaddition;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;

/**
 * What the Liquid Blaze Burner's mixin adds to the burner, whose heat method is protected (CD-MCH-01). Mixin refuses to load a class
 * of the mixin package, so this interface sits in the compat package.
 */
public interface LiquidBurnerAccess {
    /** The heat of the burner's own fuel, without the heat of a Working Superheater. */
    BlazeBurnerBlock.HeatLevel cobblemon_dynamics$ownHeat();
}
