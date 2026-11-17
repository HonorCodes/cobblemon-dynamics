package codes.honor.cobblemondynamics.mixin;

import codes.honor.cobblemondynamics.MixinMembers;
import codes.honor.cobblemondynamics.MixinState;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.MixinService;

/**
 * Skips each integration mixin whose target mod is absent, and each mixin whose target class lacks a member that the mixin shadows,
 * so a missing mod or a changed class never crashes the game. Tells {@link MixinState} which mixins injected their handler, so the
 * role of a skipped mixin turns off with one warning.
 */
public final class MixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BASE = "codes.honor.cobblemondynamics.mixin.";
    /**
     * CD-OPT-08: the mod ids of ADR-0016 whose mixins live in {@code mixin/compat/<modid>/}, as {@code compat/OptionalMods.kt} lists
     * them. The plugin runs while Mixin prepares its config, before the mod's own classes load, so it keeps its own copy, which
     * {@code MixinPluginTest} keeps in step.
     */
    private static final List<String> OPTIONAL_MODS = List.of(
            "immersiveengineering", "mekanism", "mekanismgenerators", "railcraft", "pneumaticcraft", "botanypots", "ironfurnaces",
            "cookingforblockheads", "forbidden_arcanus", "cobblemon_incubator", "ars_nouveau", "createaddition",
            "create_enchantment_industry", "irons_spellbooks", "easymagic", "enchantinginfuser", "experiencelib", "dynamictrees", "ae2");

    /** Each mixin package of an optional mod, with the mod id that it needs. */
    public static final Map<String, String> REQUIRED_MOD_BY_PACKAGE = packageMap();

    private static Map<String, String> packageMap() {
        Map<String, String> map = new HashMap<>();
        map.put(BASE + "create.", "create");
        map.put(BASE + "compat.cobblebase.", "cobblebase");
        map.put(BASE + "compat.apothic.", "apothic_enchanting");
        for (String modId : OPTIONAL_MODS) map.put(BASE + "compat." + modId + ".", modId);
        return Map.copyOf(map);
    }

    /**
     * Why the plugin skips {@code mixinClassName}, such as {@code easymagic absent}, or null when the mod of its package is
     * {@code present} or it needs none.
     */
    public static String skipReason(String mixinClassName, Predicate<String> present) {
        for (Map.Entry<String, String> entry : REQUIRED_MOD_BY_PACKAGE.entrySet()) {
            if (mixinClassName.startsWith(entry.getKey()) && !present.test(entry.getValue())) return entry.getValue() + " absent";
        }
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        MixinState.recordOffered(mixinClassName);
        String reason = skipReason(mixinClassName, modId -> FMLLoader.getLoadingModList().getModFileById(modId) != null);
        if (reason != null) {
            MixinState.recordSkipped(mixinClassName, reason);
            return false;
        }
        return targetHasShadows(targetClassName, mixinClassName);
    }

    /**
     * Mixin throws on a shadow that its target lacks, and the required config then fails the game at load (CD-FAN-17, CD-TLW-10). Mixin
     * asks this before it reads the target itself, through the same bytecode provider, which on ModLauncher serves only transformed
     * classes, so the one-argument {@code getClassNode} is the call that works.
     */
    private static boolean targetHasShadows(String targetClassName, String mixinClassName) {
        IClassBytecodeProvider bytecode = MixinService.getService().getBytecodeProvider();
        List<String> missing;
        try {
            ClassNode mixin = bytecode.getClassNode(mixinClassName);
            if (!MixinMembers.hasShadows(mixin)) return true;
            missing = MixinMembers.missingShadows(mixin, bytecode.getClassNode(targetClassName));
        } catch (ClassNotFoundException | IOException e) {
            // Mixin reads the same classes next and reports a class that it cannot find.
            return true;
        }
        if (missing.isEmpty()) return true;
        // The role gate logs the warning for the role that this mixin serves.
        LOGGER.info("Skipping mixin {}: {} has no {}", mixinClassName, targetClassName, String.join(", ", missing));
        String simpleName = targetClassName.substring(targetClassName.lastIndexOf('.') + 1);
        MixinState.recordSkipped(mixinClassName, simpleName + " has no " + String.join(", ", missing));
        return false;
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
        MixinState.recordApplied(mixinClassName, targetClass, MixinState.declaresHandler(mixinInfo.getClassNode(0)));
    }
}
