package codes.honor.cobblemondynamics.compat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import java.io.File

/**
 * Reads the classes of Apothic Enchanting 1.21.1-1.6.4, the version of COHO 2.4.0, from the test classpath. The stats hook names its
 * target in strings that no compiler checks, so a wrong name shows only in a game, as a Muse that the role registry turns off.
 */
class ApothicStatsHookTest {
    private val hook = File(
        System.getProperty("cd.projectDir"),
        "src/main/java/codes/honor/cobblemondynamics/mixin/compat/apothic/EnchantmentTableStatsMixin.java",
    ).readText()

    private val target: String by lazy { Regex("""@Mixin\(targets = "([^"]+)"\)""").find(hook)?.groupValues?.get(1) ?: error("no target") }
    private val method: String by lazy { Regex("""method = "([^"]+)"""").find(hook)?.groupValues?.get(1) ?: error("no method") }

    private fun classNode(name: String): ClassNode {
        val stream = javaClass.classLoader.getResourceAsStream(name.replace('.', '/') + ".class") ?: error("$name is not on the classpath")
        return stream.use { ClassNode().also { node -> ClassReader(it).accept(node, 0) } }
    }

    @Test
    fun `CD-MUS-07 the stats hook names a static method of the stats record, and the record has the constructor that the hook calls`() {
        assertEquals(STATS, target)
        val stats = classNode(target)
        val found = stats.methods.singleOrNull { it.name + it.desc == method }
        assertTrue(found != null && (found.access and Opcodes.ACC_STATIC) != 0, "$target has no static $method")
        val constructor = stats.methods.any { it.name == "<init>" && it.desc == "(FFFILjava/util/Set;ZZ)V" }
        assertTrue(constructor, "$target has no canonical constructor")
    }

    @Test
    fun `CD-MUS-07 the table menu of Apothic Enchanting reads the hooked stats and never vanilla's bookshelf cost`() {
        val calls = classNode(MENU).methods.flatMap { it.instructions.filterIsInstance<MethodInsnNode>() }
        val owner = STATS.replace('.', '/')
        assertTrue(calls.any { it.owner == owner && it.name + it.desc == method }, "$MENU does not call $method")
        val vanillaCost = calls.filter { it.owner == ENCHANTMENT_HELPER && it.name == "getEnchantmentCost" }
        assertTrue(vanillaCost.isEmpty(), "$MENU calls vanilla's getEnchantmentCost, which the bookshelf mixin changes")
    }

    private companion object {
        const val STATS = "dev.shadowsoffire.apothic_enchanting.table.EnchantmentTableStats"
        const val MENU = "dev.shadowsoffire.apothic_enchanting.table.ApothEnchantmentMenu"
        const val ENCHANTMENT_HELPER = "net/minecraft/world/item/enchantment/EnchantmentHelper"
    }
}
