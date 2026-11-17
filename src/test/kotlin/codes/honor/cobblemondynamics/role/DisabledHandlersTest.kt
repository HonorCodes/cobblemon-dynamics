package codes.honor.cobblemondynamics.role

import codes.honor.cobblemondynamics.engine.guarded
import codes.honor.cobblemondynamics.handler.HandlerRegistry
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DisabledHandlersTest {
    private val furnace = ResourceLocation.fromNamespaceAndPath("cobblemon_dynamics", "furnace")

    @Test
    fun `CD-PERF-05 a handler that threw is off with one warning, until a clear turns it on again`() {
        val disabled = DisabledHandlers()
        assertTrue(disabled.disable(furnace, "first"), "the first cause was not the one to log")
        assertFalse(disabled.disable(furnace, "second"), "a second cause logged again")
        assertTrue(disabled.isDisabled(furnace))
        assertTrue(disabled.clear(), "the clear found no handler off")
        assertFalse(disabled.isDisabled(furnace), "the handler stayed off after the clear")
        assertFalse(disabled.clear(), "a clear with no handler off reported one")
        assertTrue(disabled.disable(furnace, "again"), "the first cause after a clear did not log")
    }

    @Test
    fun `the role registry turns a handler that threw on again on server stop and on a role file load`() {
        val handler = ResourceLocation.fromNamespaceAndPath("cobblemon_dynamics_test", "reenable")
        HandlerRegistry.register(FakeHandler(handler))
        val worked = role("reenable_worker", setOf("fire"), handler = handler.toString())
        val roles = mapOf(worked.id to worked)
        val previous = RoleRegistry.applyForTest(roles)
        try {
            assertNotNull(RoleRegistry.current.get(worked.id), "the role did not load")
            RoleRegistry.disableHandler(handler, "boom")
            assertNull(RoleRegistry.current.get(worked.id), "the handler that threw kept its role")
            RoleRegistry.reset()
            assertNotNull(RoleRegistry.current.get(worked.id), "the server stop left the role off")
            RoleRegistry.disableHandler(handler, "boom")
            assertNull(RoleRegistry.current.get(worked.id), "the handler that threw again kept its role")
            RoleRegistry.applyForTest(roles)
            assertNotNull(RoleRegistry.current.get(worked.id), "the role file load left the role off")
        } finally {
            RoleRegistry.applyForTest(previous)
        }
    }

    /**
     * A later version of an optional mod that renames a member makes its compat handler throw a `LinkageError`, which is no
     * `Exception`. The engine's guard turns the handler's roles off and keeps the tick running. [DisabledHandlers] keeps the first
     * cause only, so the warning logs once per handler (the test above).
     */
    @Test
    fun `CD-OPT-10 a compat handler that throws NoSuchMethodError turns its roles off, and the other roles keep working`() {
        val compat = ResourceLocation.fromNamespaceAndPath("cobblemon_dynamics_test", "stoker_somemod")
        val steady = ResourceLocation.fromNamespaceAndPath("cobblemon_dynamics_test", "steady")
        HandlerRegistry.register(FakeHandler(compat))
        HandlerRegistry.register(FakeHandler(steady))
        val broken = role("stoker_somemod", setOf("fire"), handler = compat.toString())
        val working = role("steady_worker", setOf("water"), handler = steady.toString())
        val previous = RoleRegistry.applyForTest(mapOf(broken.id to broken, working.id to working))
        try {
            val handler = HandlerRegistry.get(compat)!!
            val result = assertDoesNotThrow<Boolean> {
                guarded(handler, false) { throw NoSuchMethodError("'int somemod.Machine.burnTime()'") }
            }
            assertFalse(result, "the guard did not return its fallback")
            assertNull(RoleRegistry.current.get(broken.id), "the handler that threw kept its role")
            assertNotNull(RoleRegistry.current.get(working.id), "a role of another handler turned off")
            assertTrue(broken.id in RoleRegistry.current.disabled, "the role that turned off is not named as disabled")
        } finally {
            RoleRegistry.applyForTest(previous)
        }
    }
}
