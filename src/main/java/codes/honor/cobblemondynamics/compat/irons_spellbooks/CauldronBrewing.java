package codes.honor.cobblemondynamics.compat.irons_spellbooks;

/**
 * What the Alchemist Cauldron's mixin adds to the cauldron, whose cook times are private (CD-MCH-07). Mixin refuses to load a class of
 * the mixin package, so this interface sits in the compat package.
 */
public interface CauldronBrewing {
    /** Whether a cook time runs below its end, which the Brewer's demand reads. */
    boolean cobblemon_dynamics$cooking();

    /**
     * Lets the cauldron's own ticks up to game time {@code until} advance each running cook time by {@code steps} after their own
     * step. The handler renews it on each tick of work, so it lapses within 2 ticks of the end of the work.
     */
    void cobblemon_dynamics$grant(long until, int steps);

    /** The extra steps of the cauldron's tick at game time {@code now}, while a grant holds. */
    void cobblemon_dynamics$brew(long now);
}
