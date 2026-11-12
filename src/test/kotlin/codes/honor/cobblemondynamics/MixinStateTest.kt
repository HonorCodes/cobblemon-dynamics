package codes.honor.cobblemondynamics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode

class MixinStateTest {
    private val owner = "com/example/Target"

    /** A target class with a `tick` method that calls [called], and the merged handler method [merged] that nothing calls. */
    private fun target(called: String?, merged: String = "modify\$zza000\$cobblemon_dynamics\$virtualFlow"): ClassNode {
        val node = ClassNode()
        node.name = owner
        val tick = MethodNode(Opcodes.ACC_PUBLIC, "tick", "()V", null, null)
        tick.instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
        if (called != null) tick.instructions.add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, owner, called, "()V", false))
        tick.instructions.add(InsnNode(Opcodes.RETURN))
        node.methods.add(tick)
        node.methods.add(MethodNode(Opcodes.ACC_PRIVATE, merged, "(I)I", null, null))
        return node
    }

    @Test
    fun `CD-WHL-10 a mixin counts as applied only when the target calls one of its handlers`() {
        val injected = "codes.honor.cobblemondynamics.mixin.test.InjectedMixin"
        MixinState.recordApplied(injected, target(called = "modify\$zza000\$cobblemon_dynamics\$virtualFlow"), true)
        assertTrue(MixinState.applied("test.InjectedMixin"))

        // With defaultRequire 0, a missing injection point merges the handler method but injects no call to it.
        val missed = "codes.honor.cobblemondynamics.mixin.test.MissedMixin"
        MixinState.recordApplied(missed, target(called = "redraw"), true)
        assertFalse(MixinState.applied("test.MissedMixin"))
    }

    @Test
    fun `CD-CMP-05 each handler that a target calls counts as applied by its name`() {
        val target =
            target(called = "handler\$zzc000\$cobblemon_dynamics\$partOne", merged = "handler\$zzc001\$cobblemon_dynamics\$partTwo")
        MixinState.recordApplied("codes.honor.cobblemondynamics.mixin.test.PartMixin", target, true)
        assertTrue(MixinState.handlerApplied("partOne"))
        // The merged handler that nothing calls did not apply.
        assertFalse(MixinState.handlerApplied("partTwo"))
        assertTrue(MixinState.applied("test.PartMixin"))
    }

    @Test
    fun `setForTest overrides the recorded state`() {
        val key = "test.OverriddenMixin"
        val heat = target(called = "handler\$zzb000\$cobblemon_dynamics\$heat")
        MixinState.recordApplied("codes.honor.cobblemondynamics.mixin.$key", heat, true)
        MixinState.setForTest(key, false)
        assertFalse(MixinState.applied(key))
        MixinState.setForTest(key, true)
        assertTrue(MixinState.applied(key))
    }

    @Test
    fun `CD-CMP-08 the load line reads each offered mixin as skipped with its reason, applied, with no injection or not applied yet`() {
        val base = "codes.honor.cobblemondynamics.mixin."
        MixinState.recordOffered(base + "line.SkippedMixin")
        MixinState.recordSkipped(base + "line.SkippedMixin", "cobblebase absent")
        // An accessor mixin declares no handler, so it reads applied once Mixin merged it.
        MixinState.recordOffered(base + "line.AccessorMixin")
        MixinState.recordApplied(base + "line.AccessorMixin", target(called = null, merged = "getSlots"), false)
        // A mixin with a handler that its target never calls reads no injection.
        MixinState.recordOffered(base + "line.MissedMixin")
        MixinState.recordApplied(base + "line.MissedMixin", target(called = "redraw"), true)
        MixinState.recordOffered(base + "line.LaterMixin")
        val entries = LoadLines.mixinEntries(
            MixinState.offered().filter { it.startsWith("line.") }.toSet(),
            MixinState.skipped(),
            MixinState.merged(),
            MixinState.withHandlers(),
            MixinState::applied,
        )
        val expected = listOf(
            LoadLines.MixinEntry("line.AccessorMixin", LoadLines.MixinStatus.APPLIED),
            LoadLines.MixinEntry("line.LaterMixin", LoadLines.MixinStatus.NOT_APPLIED_YET),
            LoadLines.MixinEntry("line.MissedMixin", LoadLines.MixinStatus.NO_INJECTION),
            LoadLines.MixinEntry("line.SkippedMixin", LoadLines.MixinStatus.SKIPPED, "cobblebase absent"),
        )
        assertEquals(expected, entries)
        assertFalse(MixinState.applied("line.AccessorMixin"), "applied keeps its meaning: a handler that a target calls")
    }

    @Test
    fun `CD-CMP-08 a mixin class declares a handler when a method name holds the mark`() {
        assertTrue(MixinState.declaresHandler(target(called = null)))
        assertFalse(MixinState.declaresHandler(target(called = null, merged = "getSlots")))
    }
}
