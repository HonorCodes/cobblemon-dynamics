package codes.honor.cobblemondynamics;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Which of the mod's mixins injected a call to their handler. Keys are mixin class names relative to the mixin package.
 *
 * <p>This class sits outside the mixin package: Mixin refuses to load any class from that package once its config is active.
 */
public final class MixinState {
    public static final String WATER_WHEEL = "create.WaterWheelBlockEntityMixin";
    public static final String BLAZE_BURNER = "create.BlazeBurnerBlockEntityMixin";
    public static final String ENCHANTMENT_MENU = "vanilla.EnchantmentMenuMixin";
    public static final String PASTURE_WIDGET = "cobblemon.client.PastureWidgetMixin";

    static final String MIXIN_PACKAGE = "codes.honor.cobblemondynamics.mixin.";

    /** Every handler method of the mod's mixins has this in its name, and Mixin keeps it when it renames the handler. */
    private static final String HANDLER_MARK = "cobblemon_dynamics$";

    private static final Set<String> APPLIED = ConcurrentHashMap.newKeySet();
    private static final Map<String, Boolean> OVERRIDES = new ConcurrentHashMap<>();

    private MixinState() {
    }

    public static boolean applied(String key) {
        Boolean override = OVERRIDES.get(key);
        return override != null ? override : APPLIED.contains(key);
    }

    /**
     * Called by the mixin plugin after Mixin applied {@code mixinClassName} to {@code target}. With {@code defaultRequire: 0} a missing
     * injection point applies the mixin class but injects no call, so only a call to a handler counts.
     */
    public static void recordApplied(String mixinClassName, ClassNode target) {
        if (callsHandler(target)) APPLIED.add(keyOf(mixinClassName));
    }

    public static void setForTest(String key, boolean value) {
        OVERRIDES.put(key, value);
    }

    private static String keyOf(String mixinClassName) {
        return mixinClassName.startsWith(MIXIN_PACKAGE) ? mixinClassName.substring(MIXIN_PACKAGE.length()) : mixinClassName;
    }

    private static boolean callsHandler(ClassNode target) {
        for (MethodNode method : target.methods) {
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode call && call.name.contains(HANDLER_MARK)) return true;
            }
        }
        return false;
    }
}
