package codes.honor.cobblemondynamics

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
        MixinState.recordApplied(injected, target(called = "modify\$zza000\$cobblemon_dynamics\$virtualFlow"))
        assertTrue(MixinState.applied("test.InjectedMixin"))

        // With defaultRequire 0, a missing injection point merges the handler method but injects no call to it.
        val missed = "codes.honor.cobblemondynamics.mixin.test.MissedMixin"
        MixinState.recordApplied(missed, target(called = "redraw"))
        assertFalse(MixinState.applied("test.MissedMixin"))
    }

    @Test
    fun `CD-CMP-05 each handler that a target calls counts as applied by its name`() {
        val target =
            target(called = "handler\$zzc000\$cobblemon_dynamics\$partOne", merged = "handler\$zzc001\$cobblemon_dynamics\$partTwo")
        MixinState.recordApplied("codes.honor.cobblemondynamics.mixin.test.PartMixin", target)
        assertTrue(MixinState.handlerApplied("partOne"))
        // The merged handler that nothing calls did not apply.
        assertFalse(MixinState.handlerApplied("partTwo"))
        assertTrue(MixinState.applied("test.PartMixin"))
    }

    @Test
    fun `setForTest overrides the recorded state`() {
        val key = "test.OverriddenMixin"
        MixinState.recordApplied("codes.honor.cobblemondynamics.mixin.$key", target(called = "handler\$zzb000\$cobblemon_dynamics\$heat"))
        MixinState.setForTest(key, false)
        assertFalse(MixinState.applied(key))
        MixinState.setForTest(key, true)
        assertTrue(MixinState.applied(key))
    }
}
