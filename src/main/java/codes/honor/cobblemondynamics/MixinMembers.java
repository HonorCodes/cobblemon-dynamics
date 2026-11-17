package codes.honor.cobblemondynamics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * The members that a mixin shadows and its target class lacks, and the methods that it injects and its target lacks with the
 * handler's static modifier. Mixin throws when it applies a mixin with such a shadow or such a handler, and a required config turns
 * that into a crash at load, so the mixin plugin skips the mixin instead (CD-FAN-17, CD-TLW-10).
 *
 * <p>The match is Mixin's own: a shadow needs a member of the target class itself with its name or one of its aliases, the same
 * descriptor and the same static modifier. A shadow method's name loses its prefix first. Descriptors are compared as written, which
 * holds while no shadow names a mixin class. This class sits outside the mixin package, which Mixin reserves.
 */
public final class MixinMembers {
    static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
    static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    static final String DEFAULT_PREFIX = "shadow$";

    /** A method name with no owner, wildcard, quantifier or pattern, such as {@code getAvailablePower} or {@code lambda$tick$0}. */
    private static final Pattern PLAIN_NAME = Pattern.compile("[\\w$<>]+");

    private MixinMembers() {
    }

    /** Whether {@code mixin} shadows any field or method. */
    public static boolean hasShadows(ClassNode mixin) {
        return mixin.fields.stream().anyMatch(field -> shadowOf(field.visibleAnnotations) != null)
                || mixin.methods.stream().anyMatch(method -> shadowOf(method.visibleAnnotations) != null);
    }

    /** Each shadow of {@code mixin} that {@code target} lacks, as {@code field name descriptor} or {@code method name descriptor}. */
    public static List<String> missingShadows(ClassNode mixin, ClassNode target) {
        List<String> missing = new ArrayList<>();
        for (FieldNode field : mixin.fields) {
            AnnotationNode shadow = shadowOf(field.visibleAnnotations);
            if (shadow == null) continue;
            Set<String> names = names(field.name, shadow);
            boolean found = target.fields.stream().anyMatch(candidate ->
                    names.contains(candidate.name) && candidate.desc.equals(field.desc) && isStatic(candidate.access) == isStatic(field.access));
            if (!found) missing.add("field " + field.name + " " + field.desc);
        }
        for (MethodNode method : mixin.methods) {
            AnnotationNode shadow = shadowOf(method.visibleAnnotations);
            if (shadow == null) continue;
            String prefix = stringValue(shadow, "prefix", DEFAULT_PREFIX);
            String name = method.name.startsWith(prefix) ? method.name.substring(prefix.length()) : method.name;
            Set<String> names = names(name, shadow);
            boolean found = target.methods.stream().anyMatch(candidate ->
                    names.contains(candidate.name) && candidate.desc.equals(method.desc) && isStatic(candidate.access) == isStatic(method.access));
            if (!found) missing.add("method " + name + " " + method.desc);
        }
        return missing;
    }

    /** Whether {@code mixin} has a handler of {@code @Inject}. */
    public static boolean hasInjects(ClassNode mixin) {
        return mixin.methods.stream().anyMatch(method -> annotation(method.visibleAnnotations, INJECT) != null);
    }

    /**
     * Each method that an {@code @Inject} of {@code mixin} names and {@code target} lacks with the handler's static modifier, as
     * {@code instance method name desc} or {@code static method name desc}. Mixin throws at load on a handler whose static modifier
     * differs from its target method's, also with {@code defaultRequire: 0}. A selector with no descriptor takes the first method of
     * its name, as Mixin's selector does. A selector with an owner, a wildcard or a pattern is not checked.
     */
    public static List<String> missingInjectTargets(ClassNode mixin, ClassNode target) {
        List<String> missing = new ArrayList<>();
        for (MethodNode handler : mixin.methods) {
            AnnotationNode inject = annotation(handler.visibleAnnotations, INJECT);
            if (inject == null || !(value(inject, "method") instanceof List<?> selectors)) continue;
            boolean isStatic = isStatic(handler.access);
            for (Object selector : selectors) {
                if (!(selector instanceof String text)) continue;
                int paren = text.indexOf('(');
                String name = paren < 0 ? text : text.substring(0, paren);
                if (!PLAIN_NAME.matcher(name).matches()) continue;
                String desc = paren < 0 ? null : text.substring(paren);
                MethodNode found = target.methods.stream()
                        .filter(candidate -> candidate.name.equals(name) && (desc == null || candidate.desc.equals(desc)))
                        .findFirst()
                        .orElse(null);
                if (found == null || isStatic(found.access) != isStatic) {
                    missing.add((isStatic ? "static" : "instance") + " method " + name + (desc == null ? "" : " " + desc));
                }
            }
        }
        return missing;
    }

    private static AnnotationNode shadowOf(List<AnnotationNode> annotations) {
        return annotation(annotations, SHADOW);
    }

    private static AnnotationNode annotation(List<AnnotationNode> annotations, String desc) {
        if (annotations == null) return null;
        for (AnnotationNode annotation : annotations) {
            if (desc.equals(annotation.desc)) return annotation;
        }
        return null;
    }

    /** {@code name} and the annotation's {@code aliases}. */
    private static Set<String> names(String name, AnnotationNode shadow) {
        Set<String> names = new HashSet<>();
        names.add(name);
        Object aliases = value(shadow, "aliases");
        if (aliases instanceof List<?> list) {
            for (Object alias : list) {
                if (alias instanceof String s) names.add(s);
            }
        }
        return names;
    }

    private static String stringValue(AnnotationNode annotation, String key, String fallback) {
        return value(annotation, key) instanceof String s ? s : fallback;
    }

    /** ASM keeps an annotation's values as a flat list of names and values. */
    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values == null) return null;
        for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        }
        return null;
    }

    private static boolean isStatic(int access) {
        return (access & Opcodes.ACC_STATIC) != 0;
    }
}
