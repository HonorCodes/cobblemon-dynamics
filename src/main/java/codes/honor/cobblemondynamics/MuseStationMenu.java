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
}
