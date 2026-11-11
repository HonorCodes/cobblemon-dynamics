package codes.honor.cobblemondynamics.role

import codes.honor.cobblemondynamics.handler.HandlerRegistry
import net.minecraft.resources.ResourceLocation
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
}
