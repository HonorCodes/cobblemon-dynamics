package codes.honor.cobblemondynamics.compat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class CobblebaseHooksTest {
    private val mixins = File(System.getProperty("cd.projectDir"), "src/main/java/codes/honor/cobblemondynamics/mixin/compat/cobblebase")

    @Test
    fun `CD-CMP-05 a Cobblebase without a hooked class or method leaves those hooks out, named in one warning`() {
        val loaded = { target: String -> target != "notlown.cobblebase.core.PassiveXp" }
        val missing = CobblebaseHooks.missing(load = loaded, applied = { it != "idle" })
        assertEquals(listOf("idle", "passiveXp"), missing)
    }

    @Test
    fun `CD-CMP-05 with every hook in place nothing is missing`() {
        assertEquals(emptyList<String>(), CobblebaseHooks.missing(load = { true }, applied = { true }))
    }

    /** No mixin applies in a unit test, so the check finds every hook missing, and it warns once without throwing. */
    @Test
    fun `CD-CMP-05 the check warns once and returns`() {
        val warnings = mutableListOf<String>()
        CobblebaseHooks.check(warnings::add)
        assertEquals(1, warnings.size, warnings.toString())
        for (handler in CobblebaseHooks.HOOKS.values.flatten()) assertTrue(handler in warnings.single(), "$handler not named in $warnings")
    }

    /** Each listed hook is a handler method of a mixin in `mixin/compat/cobblebase`, so the check reads the right names. */
    @Test
    fun `every listed hook is a handler of a Cobblebase mixin for its class`() {
        for ((target, handlers) in CobblebaseHooks.HOOKS) {
            val mixin = mixins.listFiles().orEmpty().single { "targets = \"$target\"" in it.readText() }
            val source = mixin.readText()
            for (handler in handlers) assertTrue("void cobblemon_dynamics\$$handler(" in source, "${mixin.name} has no handler $handler")
            assertEquals(handlers.size, Regex("void cobblemon_dynamics\\$").findAll(source).count(), "${mixin.name} has unlisted handlers")
        }
    }
}
