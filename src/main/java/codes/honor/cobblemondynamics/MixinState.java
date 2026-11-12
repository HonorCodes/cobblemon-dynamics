package codes.honor.cobblemondynamics;

import java.util.HashSet;
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
    public static final String ITEM_USE_OVERRIDES = "create.ItemUseOverridesMixin";
    public static final String AIR_CURRENT = "create.AirCurrentMixin";
    public static final String WINDMILL_BEARING = "create.WindmillBearingBlockEntityMixin";
    public static final String ENCHANTMENT_MENU = "vanilla.EnchantmentMenuMixin";
    public static final String WORKER_PUSH = "vanilla.WorkerPushMixin";
    public static final String PASTURE_WIDGET = "cobblemon.client.PastureWidgetMixin";
    public static final String PASTURE_LIST = "cobblemon.client.PasturePokemonScrollListMixin";
    public static final String COBBLEBASE_AMBIENT = "compat.cobblebase.AmbientBehaviorMixin";
    public static final String COBBLEBASE_NAVIGATION = "compat.cobblebase.NavigationHelperMixin";
    public static final String COBBLEBASE_BASE_MANAGER = "compat.cobblebase.BaseManagerMixin";
    public static final String COBBLEBASE_PASSIVE_XP = "compat.cobblebase.PassiveXpMixin";
    public static final String APOTHIC_TABLE_STATS = "compat.apothic.EnchantmentTableStatsMixin";

    static final String MIXIN_PACKAGE = "codes.honor.cobblemondynamics.mixin.";

    /** Every handler method of the mod's mixins has this in its name, and Mixin keeps it when it renames the handler. */
    private static final String HANDLER_MARK = "cobblemon_dynamics$";

    private static final Set<String> APPLIED = ConcurrentHashMap.newKeySet();
    private static final Set<String> CALLED_HANDLERS = ConcurrentHashMap.newKeySet();
    private static final Map<String, Boolean> OVERRIDES = new ConcurrentHashMap<>();

    // The load line of CD-CMP-08: what Mixin offered the plugin, what the plugin skipped and why, what Mixin merged, and which of those
    // declare a handler. Mixin fills them once while the game loads, and nothing clears them.
    private static final Set<String> OFFERED = ConcurrentHashMap.newKeySet();
    private static final Map<String, String> SKIPPED = new ConcurrentHashMap<>();
    private static final Set<String> MERGED = ConcurrentHashMap.newKeySet();
    private static final Set<String> WITH_HANDLERS = ConcurrentHashMap.newKeySet();

    private MixinState() {
    }

    public static boolean applied(String key) {
        Boolean override = OVERRIDES.get(key);
        return override != null ? override : APPLIED.contains(key);
    }

    /**
     * True when some target calls the handler method {@code cobblemon_dynamics$<name>}, so its one injection point matched. A mixin
     * with several handlers can apply in part, and this tells which part.
     */
    public static boolean handlerApplied(String name) {
        return CALLED_HANDLERS.contains(name);
    }

    /**
     * Called by the mixin plugin after Mixin applied {@code mixinClassName} to {@code target}. With {@code defaultRequire: 0} a missing
     * injection point applies the mixin class but injects no call, so only a call to a handler counts.
     */
    public static void recordApplied(String mixinClassName, ClassNode target, boolean hasHandlers) {
        String key = keyOf(mixinClassName);
        MERGED.add(key);
        if (hasHandlers) WITH_HANDLERS.add(key);
        Set<String> called = calledHandlers(target);
        CALLED_HANDLERS.addAll(called);
        if (!called.isEmpty()) APPLIED.add(key);
    }

    /** Called by the mixin plugin for each mixin that Mixin offers it on this side, while Mixin prepares the config (CD-CMP-08). */
    public static void recordOffered(String mixinClassName) {
        OFFERED.add(keyOf(mixinClassName));
    }

    /** Called by the mixin plugin for each mixin that it skips, with the reason, such as {@code cobblebase absent}. */
    public static void recordSkipped(String mixinClassName, String reason) {
        SKIPPED.put(keyOf(mixinClassName), reason);
    }

    public static Set<String> offered() {
        return Set.copyOf(OFFERED);
    }

    public static Map<String, String> skipped() {
        return Map.copyOf(SKIPPED);
    }

    public static Set<String> merged() {
        return Set.copyOf(MERGED);
    }

    public static Set<String> withHandlers() {
        return Set.copyOf(WITH_HANDLERS);
    }

    /** True when the mixin class declares a handler, a method whose name holds the mark. An accessor mixin declares none. */
    public static boolean declaresHandler(ClassNode mixin) {
        for (MethodNode method : mixin.methods) {
            if (method.name.contains(HANDLER_MARK)) return true;
        }
        return false;
    }

    public static void setForTest(String key, boolean value) {
        OVERRIDES.put(key, value);
    }

    private static String keyOf(String mixinClassName) {
        return mixinClassName.startsWith(MIXIN_PACKAGE) ? mixinClassName.substring(MIXIN_PACKAGE.length()) : mixinClassName;
    }

    /** The names after the mark of every handler that [target] calls. */
    private static Set<String> calledHandlers(ClassNode target) {
        Set<String> names = new HashSet<>();
        for (MethodNode method : target.methods) {
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode call && call.name.contains(HANDLER_MARK)) {
                    names.add(call.name.substring(call.name.indexOf(HANDLER_MARK) + HANDLER_MARK.length()));
                }
            }
        }
        return names;
    }
}
