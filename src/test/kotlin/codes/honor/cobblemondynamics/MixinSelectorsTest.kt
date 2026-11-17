package codes.honor.cobblemondynamics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class MixinSelectorsTest {
    private val sources: List<File> by lazy {
        File(System.getProperty("cd.projectDir"), "src/main/java/codes/honor/cobblemondynamics/mixin").walk()
            .filter { it.extension == "java" }
            .toList()
    }

    /** Each selector of the `method` of each injector, with its file. */
    private val selectors: List<Pair<String, String>> by lazy {
        sources.flatMap { file ->
            INJECTOR.findAll(file.readText()).flatMap { match ->
                SELECTOR.findAll(match.groupValues[2]).map { file.name to it.groupValues[1] }
            }
        }
    }

    /**
     * The mixin plugin compares a selector's descriptor with the target's methods and skips the mixin when the method changed. A selector
     * with no descriptor matches any method of its name, so a changed signature passes the plugin and fails the game at load.
     */
    @Test
    fun `every injector names its target method with its full descriptor`() {
        val injectors = sources.sumOf { ANY_INJECTOR.findAll(it.readText()).count() }
        assertTrue(injectors > 0, "no injector in the mixin sources")
        assertEquals(injectors, sources.sumOf { INJECTOR.findAll(it.readText()).count() }, "an injector does not name its method first")
        val bare = selectors.filterNot { (_, selector) -> DESCRIPTOR.matches(selector) }
        assertTrue(bare.isEmpty(), "selectors with no full descriptor: $bare")
    }

    /** Iron's Spells 'n Spellbooks 3.16.3 (javap): `public static void serverTick(Level, BlockPos, BlockState, AlchemistCauldronTile)`. */
    @Test
    fun `CD-MCH-07 the cauldron hook names the descriptor of the cauldron's server tick`() {
        val cauldron = "serverTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;" +
            "Lnet/minecraft/world/level/block/state/BlockState;Lio/redspace/ironsspellbooks/block/alchemist_cauldron/AlchemistCauldronTile;)V"
        assertTrue("AlchemistCauldronMixin.java" to cauldron in selectors, "the cauldron hook has another selector: $selectors")
    }

    private companion object {
        const val INJECTORS = "Inject|ModifyArg|ModifyArgs|ModifyVariable|Redirect|ModifyConstant|ModifyExpressionValue|" +
            "ModifyReturnValue|WrapOperation|WrapWithCondition"
        val ANY_INJECTOR = Regex("""@($INJECTORS)\s*\(""")
        val INJECTOR = Regex("""@($INJECTORS)\s*\(\s*method\s*=\s*(\{[^}]*}|"[^"]*")""")
        val SELECTOR = Regex(""""([^"]*)"""")
        private const val TYPE = """\[*(L[\w/$]+;|[ZBCSIJFD])"""
        val DESCRIPTOR = Regex("""[\w$<>]+\(($TYPE)*\)($TYPE|V)""")
    }
}
