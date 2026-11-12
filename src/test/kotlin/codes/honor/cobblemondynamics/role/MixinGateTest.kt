package codes.honor.cobblemondynamics.role

import codes.honor.cobblemondynamics.MixinState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MixinGateTest {
    private val wheel = role("wheel_driver", setOf("water"), handler = "cobblemon_dynamics:water_wheel")
    private val superheater = role("superheater", setOf("dragon"), priority = 10, handler = "cobblemon_dynamics:burner_heat")

    /** A data pack's own role on the Superheater's handler, so that one handler serves two roles. */
    private val packBurner = role("pack_burner", setOf("ice"), handler = "cobblemon_dynamics:burner_heat")
    private val stoker = role("stoker", setOf("fire"), handler = "cobblemon_dynamics:furnace")
    private val muse = role("muse", setOf("fairy"), autoAssign = false, handler = "cobblemon_dynamics:enchanting")

    private fun set(vararg roles: RoleDefinition) = RoleSet(roles.associateBy { it.id }, { FakeHandler(it) }, emptySet()) { }

    @Test
    fun `CD-WHL-10 CD-BRN-07 a missing mixin disables its roles and warns once`() {
        val warnings = mutableListOf<String>()
        val gated = MixinGate.apply(
            set(wheel, superheater, packBurner),
            applied = { it != MixinState.WATER_WHEEL && it != MixinState.BLAZE_BURNER },
            warn = warnings::add,
        )
        assertTrue(gated.active.isEmpty())
        assertEquals(2, warnings.size) // one per handler, not per role
        assertTrue(warnings.any { "cobblemon_dynamics:water_wheel" in it && MixinState.WATER_WHEEL in it }, warnings.toString())
        assertTrue(warnings.any { "cobblemon_dynamics:burner_heat" in it && MixinState.BLAZE_BURNER in it }, warnings.toString())
    }

    /** No mixin applies in a unit test, so the real flag of the enchanting-table mixin starts false. */
    @Test
    fun `CD-MUS-06 a missing enchanting-table mixin disables the Muse with one warning`() {
        val warnings = mutableListOf<String>()
        val gated = MixinGate.apply(set(muse, stoker), MixinState::applied, warnings::add)
        assertEquals(listOf(stoker.id), gated.active.map { it.definition.id })
        assertEquals(1, warnings.size)
        assertTrue(
            "cobblemon_dynamics:enchanting" in warnings.single() && MixinState.ENCHANTMENT_MENU in warnings.single(),
            warnings.toString(),
        )
    }

    @Test
    fun `CD-MUS-06 the Muse stays on while its mixin applied`() {
        val warnings = mutableListOf<String>()
        val gated = MixinGate.apply(set(muse, stoker), applied = { it == MixinState.ENCHANTMENT_MENU }, warn = warnings::add)
        assertEquals(listOf(muse.id, stoker.id), gated.active.map { it.definition.id })
        assertTrue(warnings.isEmpty(), warnings.toString())
    }

    @Test
    fun `CD-CMP-10 the gate adds each role that it removes to disabled, with its mixin, and keeps the earlier reasons`() {
        val set = RoleSet(mapOf(wheel.id to wheel, stoker.id to stoker, muse.id to muse), { FakeHandler(it) }, setOf(muse.id)) { }
        val gated = MixinGate.apply(set, applied = { it != MixinState.WATER_WHEEL }, warn = { })
        val expected = mapOf(
            muse.id to "roles.disabled",
            wheel.id to "its mixin ${MixinState.WATER_WHEEL} did not apply",
        )
        assertEquals(expected, gated.disabled)
        assertEquals(listOf(stoker.id), gated.active.map { it.definition.id })
    }

    @Test
    fun `CD-FAN-17 a missing air current mixin disables every Fan Catalyst role with one warning`() {
        val roles = listOf("fire", "water", "ghost").map {
            role("fan_catalyst_$it", setOf(it), autoAssign = false, handler = "cobblemon_dynamics:fan_catalyst")
        }
        val set = RoleSet(roles.associateBy { it.id }, { FakeHandler(it) }, emptySet()) { }
        val warnings = mutableListOf<String>()
        val gated = MixinGate.apply(set, applied = { it != MixinState.AIR_CURRENT }, warn = warnings::add)
        assertTrue(gated.active.isEmpty())
        assertEquals(1, warnings.size)
    }

    @Test
    fun `CD-TLW-10 a missing windmill mixin disables Tailwind with one warning`() {
        val tailwind = role("tailwind", setOf("flying"), autoAssign = false, handler = "cobblemon_dynamics:tailwind")
        val set = RoleSet(mapOf(tailwind.id to tailwind), { FakeHandler(it) }, emptySet()) { }
        val warnings = mutableListOf<String>()
        val gated = MixinGate.apply(set, applied = { it != MixinState.WINDMILL_BEARING }, warn = warnings::add)
        assertTrue(gated.active.isEmpty())
        assertEquals(1, warnings.size)
    }

    @Test
    fun `CD-WHL-10 an applied mixin keeps its roles, and a role that needs no mixin stays on`() {
        val warnings = mutableListOf<String>()
        val gated = MixinGate.apply(set(wheel, superheater, stoker), applied = { it == MixinState.WATER_WHEEL }, warn = warnings::add)
        assertEquals(listOf(stoker.id, wheel.id), gated.active.map { it.definition.id })
        assertEquals(1, warnings.size)
        assertTrue("cobblemon_dynamics:burner_heat" in warnings.single())
    }
}
