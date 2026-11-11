package codes.honor.cobblemondynamics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * The members that a mixin shadows and its target class lacks. Mixin throws when it applies a mixin with such a shadow, and a required
 * config turns that into a crash at load, so the mixin plugin skips the mixin instead (CD-FAN-17, CD-TLW-10).
 *
 * <p>The match is Mixin's own: a shadow needs a member of the target class itself with its name or one of its aliases, the same
 * descriptor and the same static modifier. A shadow method's name loses its prefix first. Descriptors are compared as written, which
 * holds while no shadow names a mixin class. This class sits outside the mixin package, which Mixin reserves.
 */
public final class MixinMembers {
    static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
    static final String DEFAULT_PREFIX = "shadow$";

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

    private static AnnotationNode shadowOf(List<AnnotationNode> annotations) {
        if (annotations == null) return null;
        for (AnnotationNode annotation : annotations) {
            if (SHADOW.equals(annotation.desc)) return annotation;
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
