package codes.honor.cobblemondynamics.mixin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Skips each integration mixin whose target mod is absent, so a missing mod never crashes the game. */
public final class MixinPlugin implements IMixinConfigPlugin {
    private static final String BASE = "codes.honor.cobblemondynamics.mixin.";
    private static final Map<String, String> REQUIRED_MOD_BY_PACKAGE = Map.of(
            BASE + "create.", "create",
            BASE + "compat.cobblebase.", "cobblebase");

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        for (Map.Entry<String, String> entry : REQUIRED_MOD_BY_PACKAGE.entrySet()) {
            if (mixinClassName.startsWith(entry.getKey())) {
                return FMLLoader.getLoadingModList().getModFileById(entry.getValue()) != null;
            }
        }
        return true;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
