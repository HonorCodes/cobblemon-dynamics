package codes.honor.cobblemondynamics;

import net.minecraft.world.inventory.ContainerLevelAccess;

/**
 * The menu of another mod's enchanting station that is no {@code EnchantmentMenu}, such as Enchanting Infuser's, which a mixin of its
 * compat package makes name its block (CD-FIX-06), so that the Muse's open-menu check finds it.
 *
 * <p>This interface sits outside the mixin package: Mixin refuses to load any class from that package once its config is active.
 */
public interface MuseStationMenu {
    ContainerLevelAccess cobblemon_dynamics$station();

    /**
     * Counts the station's power again when a Muse starts or stops, and keeps each level that the player chose within what the new
     * power offers. The station's own update for a new item would reset those levels (CD-FIX-06).
     */
    void cobblemon_dynamics$refreshPower();
}
