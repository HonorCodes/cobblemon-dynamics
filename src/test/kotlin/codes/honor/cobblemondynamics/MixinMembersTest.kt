package codes.honor.cobblemondynamics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

class MixinMembersTest {
    private val list = "Ljava/util/List;"

    private fun shadow(vararg values: Any): AnnotationNode =
        AnnotationNode(MixinMembers.SHADOW).also { node -> if (values.isNotEmpty()) node.values = values.toMutableList() }

    private fun field(name: String, desc: String = list, access: Int = Opcodes.ACC_PROTECTED, shadow: AnnotationNode? = null) =
        FieldNode(access, name, desc, null, null).also { node -> shadow?.let { node.visibleAnnotations = mutableListOf(it) } }

    private fun method(name: String, desc: String, access: Int = Opcodes.ACC_PUBLIC, shadow: AnnotationNode? = null) =
        MethodNode(access, name, desc, null, null).also { node -> shadow?.let { node.visibleAnnotations = mutableListOf(it) } }

    private fun classNode(name: String, fields: List<FieldNode> = emptyList(), methods: List<MethodNode> = emptyList()) = ClassNode().also {
        it.name = name
        it.fields.addAll(fields)
        it.methods.addAll(methods)
    }

    /** The two fields that `AirCurrentMixin` shadows, with a handler method that is no shadow. */
    private val airCurrentMixin = classNode(
        "codes/honor/cobblemondynamics/mixin/create/AirCurrentMixin",
        fields = listOf(field("caughtEntities", shadow = shadow()), field("affectedItemHandlers", shadow = shadow())),
        methods = listOf(method("cobblemon_dynamics\$exemptWorkers", "(Lnet/minecraft/world/level/Level;)V", Opcodes.ACC_PRIVATE)),
    )

    private fun airCurrent(vararg fields: FieldNode) = classNode("com/simibubi/create/content/kinetics/fan/AirCurrent", fields.toList())

    @Test
    fun `CD-FAN-17 a target with every shadowed field lacks nothing`() {
        val target = airCurrent(field("caughtEntities"), field("affectedItemHandlers"), field("pushing", "Z", Opcodes.ACC_PUBLIC))
        assertTrue(MixinMembers.hasShadows(airCurrentMixin))
        assertEquals(emptyList<String>(), MixinMembers.missingShadows(airCurrentMixin, target))
    }

    /** A Create update that renamed `caughtEntities` would make Mixin throw on the shadow, and the required config crash the game. */
    @Test
    fun `CD-FAN-17 a renamed shadowed field is missing`() {
        val target = airCurrent(field("entitiesInCurrent"), field("affectedItemHandlers"))
        assertEquals(listOf("field caughtEntities $list"), MixinMembers.missingShadows(airCurrentMixin, target))
    }

    @Test
    fun `a shadowed field needs the same descriptor and the same static modifier`() {
        val retyped = airCurrent(field("caughtEntities", "Ljava/util/Set;"), field("affectedItemHandlers"))
        assertEquals(listOf("field caughtEntities $list"), MixinMembers.missingShadows(airCurrentMixin, retyped))
        val static = airCurrent(field("caughtEntities", access = Opcodes.ACC_STATIC), field("affectedItemHandlers"))
        assertEquals(listOf("field caughtEntities $list"), MixinMembers.missingShadows(airCurrentMixin, static))
    }

    @Test
    fun `a shadow alias matches a target member of that name`() {
        val mixin = classNode("Mixin", fields = listOf(field("caughtEntities", shadow = shadow("aliases", listOf("entities")))))
        assertEquals(emptyList<String>(), MixinMembers.missingShadows(mixin, airCurrent(field("entities"))))
    }

    @Test
    fun `a shadowed method loses its prefix, and needs its descriptor`() {
        val mixin = classNode(
            "Mixin",
            methods = listOf(
                method("shadow\$getSpeed", "()F", Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT, shadow()),
                method("speedOf", "(I)F", Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT, shadow("prefix", "speed")),
            ),
        )
        val target = classNode("Target", methods = listOf(method("getSpeed", "()F"), method("Of", "(I)F")))
        assertEquals(emptyList<String>(), MixinMembers.missingShadows(mixin, target))
        val changed = classNode("Target", methods = listOf(method("getSpeed", "()D"), method("Of", "(I)F")))
        assertEquals(listOf("method getSpeed ()F"), MixinMembers.missingShadows(mixin, changed))
    }

    @Test
    fun `a mixin with no shadow needs nothing of its target`() {
        val mixin = classNode("Mixin", listOf(field("cobblemon_dynamics\$flow", "I")), listOf(method("cobblemon_dynamics\$heat", "()V")))
        assertFalse(MixinMembers.hasShadows(mixin))
        assertEquals(emptyList<String>(), MixinMembers.missingShadows(mixin, classNode("Target")))
    }
}
