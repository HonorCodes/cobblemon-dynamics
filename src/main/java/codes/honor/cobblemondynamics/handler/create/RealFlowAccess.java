package codes.honor.cobblemondynamics.handler.create;

/**
 * What {@code WaterWheelBlockEntityMixin} adds to Create's water wheel. It sits outside the mixin package, because Mixin refuses to
 * load any class of that package once its config is active.
 */
public interface RealFlowAccess {
    /** The flow score that real fluid gave the wheel at its last recompute, before any virtual flow. */
    int cobblemon_dynamics$realFlowScore();
}
