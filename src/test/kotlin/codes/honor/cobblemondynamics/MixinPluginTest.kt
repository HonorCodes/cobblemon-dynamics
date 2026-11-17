package codes.honor.cobblemondynamics

import codes.honor.cobblemondynamics.compat.OptionalMods
import codes.honor.cobblemondynamics.mixin.MixinPlugin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import java.io.File

class MixinPluginTest {
    private val base = "codes.honor.cobblemondynamics.mixin."

    @Test
    fun `CD-OPT-08 the package map holds the mixin package of each optional mod with its mod id`() {
        val map = MixinPlugin.REQUIRED_MOD_BY_PACKAGE
        for (mod in OptionalMods.ALL.filter { it.compat != null }) {
            assertEquals(mod.modId, map["${base}compat.${mod.compat}."], "the mixin package of ${mod.modId}")
        }
        assertEquals("create", map["${base}create."])
        assertEquals("cobblebase", map["${base}compat.cobblebase."])
        // A mixin package of the source tree that the map lacks would apply without its mod.
        val packages = File(System.getProperty("cd.projectDir"), "src/main/java/codes/honor/cobblemondynamics/mixin/compat")
            .listFiles { file -> file.isDirectory }.orEmpty()
        assertTrue(packages.isNotEmpty())
        for (dir in packages) assertTrue("${base}compat.${dir.name}." in map, "the map lacks mixin/compat/${dir.name}/")
    }

    @Test
    fun `CD-OPT-08 a mixin of an absent mod is skipped with its reason`() {
        val mixin = "${base}compat.easymagic.ModEnchantmentMenuMixin"
        assertEquals("easymagic absent", MixinPlugin.skipReason(mixin) { false })
        assertNull(MixinPlugin.skipReason(mixin) { it == "easymagic" })
        assertEquals("mekanismgenerators absent", MixinPlugin.skipReason("${base}compat.mekanismgenerators.WindMixin") { it == "mekanism" })
        assertNull(MixinPlugin.skipReason("${base}vanilla.WorkerPushMixin") { false }, "a vanilla mixin needs no mod")
    }

    /** With `defaultRequire: 0`, Mixin skips a missing injection point, but throws at load on a handler whose target turned static. */
    @Test
    fun `a mixin whose injected method turned static or went is skipped with its reason`() {
        val power = "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)I"
        val inject = AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Inject;").also {
            it.values = mutableListOf("method", listOf("getAvailablePower$power"))
        }
        val handler = MethodNode(Opcodes.ACC_PRIVATE, "cobblemon_dynamics\$musePower", "(Ljava/lang/Object;)V", null, null)
        handler.visibleAnnotations = mutableListOf(inject)
        val mixin = ClassNode().also {
            it.name = "codes/honor/cobblemondynamics/mixin/compat/enchantinginfuser/InfuserMenuMixin"
            it.methods.add(handler)
        }
        fun infuser(access: Int?) = ClassNode().also { node ->
            node.name = "fuzs/enchantinginfuser/world/inventory/InfuserMenu"
            if (access != null) node.methods.add(MethodNode(access, "getAvailablePower", power, null, null))
        }
        assertNull(MixinPlugin.memberReason(mixin, infuser(Opcodes.ACC_PRIVATE)))
        val reason = "InfuserMenu has no instance method getAvailablePower $power"
        val turnedStatic = infuser(Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC)
        assertEquals(reason, MixinPlugin.memberReason(mixin, turnedStatic), "the method turned static")
        assertEquals(reason, MixinPlugin.memberReason(mixin, infuser(null)), "the method went")
    }
}
