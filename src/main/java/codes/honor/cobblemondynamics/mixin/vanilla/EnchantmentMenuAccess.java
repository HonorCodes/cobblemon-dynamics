package codes.honor.cobblemondynamics.mixin.vanilla;

import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The enchanting table that a menu belongs to, which vanilla keeps private (CD-MUS-05). Mixin lets other code load an accessor. */
@Mixin(EnchantmentMenu.class)
public interface EnchantmentMenuAccess {
    @Accessor("access")
    ContainerLevelAccess cobblemon_dynamics$access();
}
