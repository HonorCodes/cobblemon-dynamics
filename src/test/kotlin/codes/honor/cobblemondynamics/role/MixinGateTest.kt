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

    /** Apothic Enchanting's table menu never reads the vanilla menu's bookshelf count, so there the Muse needs the stats hook. */
    @Test
    fun `CD-MUS-06 CD-MUS-07 with Apothic Enchanting the Muse needs the stats mixin, not the menu mixin`() {
        assertEquals(MixinGate.REQUIRED, MixinGate.required(apothicEnchanting = false))
        val required = MixinGate.required(apothicEnchanting = true)
        val warnings = mutableListOf<String>()
        val gated = MixinGate.apply(set(muse, stoker), { it == MixinState.ENCHANTMENT_MENU }, required, warnings::add)
        assertEquals(listOf(stoker.id), gated.active.map { it.definition.id })
        assertTrue(
            "cobblemon_dynamics:enchanting" in warnings.single() && MixinState.APOTHIC_TABLE_STATS in warnings.single(),
            warnings.toString(),
        )
        val kept = MixinGate.apply(set(muse, stoker), { it == MixinState.APOTHIC_TABLE_STATS }, required, warnings::add)
        assertEquals(listOf(muse.id, stoker.id), kept.active.map { it.definition.id })
        assertEquals(1, warnings.size, warnings.toString())
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

    /** CD-OPT-09: the Watermill and the Windmill of Immersive Engineering work only through their hooks. */
    @Test
    fun `CD-KIN-02 CD-AIR-05 a missing IE mill mixin disables its role only, with one warning`() {
        val watermill = role("wheel_driver_immersiveengineering", setOf("water"), handler = "cobblemon_dynamics:ie_watermill")
        val windmill =
            role("tailwind_immersiveengineering", setOf("flying"), autoAssign = false, handler = "cobblemon_dynamics:ie_windmill")
        val kiln = role("stoker_immersiveengineering", setOf("fire"), handler = "cobblemon_dynamics:ie_furnace")
        assertEquals(MixinState.IE_WATERMILL, MixinGate.REQUIRED[watermill.handler])
        assertEquals(MixinState.IE_WINDMILL, MixinGate.REQUIRED[windmill.handler])
        val warnings = mutableListOf<String>()
        val gated = MixinGate.apply(set(watermill, windmill, kiln), applied = { it != MixinState.IE_WINDMILL }, warn = warnings::add)
        assertEquals(listOf(kiln.id, watermill.id), gated.active.map { it.definition.id })
        assertTrue(MixinState.IE_WINDMILL in warnings.single(), warnings.toString())
    }

    @Test
    fun `CD-AIR-02 a missing Wind Generator mixin disables Tailwind on Mekanism with one warning`() {
        val wind = role("tailwind_mekanism", setOf("flying"), autoAssign = false, handler = "cobblemon_dynamics:wind_generator")
        assertEquals(MixinState.WIND_GENERATOR, MixinGate.REQUIRED[wind.handler])
        val warnings = mutableListOf<String>()
        val gated = MixinGate.apply(set(wind, stoker), applied = { it != MixinState.WIND_GENERATOR }, warn = warnings::add)
        assertEquals(listOf(stoker.id), gated.active.map { it.definition.id })
        assertTrue(MixinState.WIND_GENERATOR in warnings.single(), warnings.toString())
    }

    /** Easy Magic's table and an infuser stand for the targets of the Muse's shared handler, which Task 2 adds. */
    @Test
    fun `CD-OPT-09 a target mixin that did not apply turns off that target only, with one warning`() {
        val table = "easymagic:enchanting_table"
        val infuser = "enchantinginfuser:enchanting_infuser"
        val required = mapOf(
            table to "compat.easymagic.ModEnchantmentMenuMixin",
            infuser to "compat.enchantinginfuser.InfuserMenuMixin",
        )
        val warnings = mutableListOf<String>()
        val off = MixinGate.offTargets(required, applied = { it == required[infuser] }, loaded = { true }, warn = warnings::add)
        assertEquals(setOf(table), off)
        assertEquals(1, warnings.size, warnings.toString())
        assertTrue(table in warnings.single() && required.getValue(table) in warnings.single(), warnings.toString())
        // The Muse itself stays on: a target mixin never reaches the role gate.
        val gated = MixinGate.apply(set(muse, stoker), applied = { it == MixinState.ENCHANTMENT_MENU }, warn = warnings::add)
        assertEquals(listOf(muse.id, stoker.id), gated.active.map { it.definition.id })
        assertEquals(1, warnings.size, warnings.toString())
    }

    @Test
    fun `CD-OPT-09 each target mixin of 1_2 that did not apply turns its target off and names it once`() {
        assertTrue(MixinGate.REQUIRED_FOR_TARGET.isNotEmpty())
        for ((target, mixin) in MixinGate.REQUIRED_FOR_TARGET) {
            val warnings = mutableListOf<String>()
            val off = MixinGate.offTargets(MixinGate.REQUIRED_FOR_TARGET, applied = {
                it != mixin
            }, loaded = { true }, warn = warnings::add)
            // Targets of one mixin, such as two infusers of one menu, go off together.
            assertEquals(MixinGate.REQUIRED_FOR_TARGET.filterValues { it == mixin }.keys, off)
            assertEquals(1, warnings.count { target in it && mixin in it }, warnings.toString())
        }
        assertTrue(MixinGate.offTargets(MixinGate.REQUIRED_FOR_TARGET, applied = { true }, loaded = { true }) { error(it) }.isEmpty())
    }

    /** Without its mod, a target's mixin is skipped, and the target does not exist: no warning reads as a setup problem. */
    @Test
    fun `CD-OPT-09 the target of an absent mod gives no warning`() {
        val off = MixinGate.offTargets(MixinGate.REQUIRED_FOR_TARGET, applied = { false }, loaded = { false }) { error(it) }
        assertTrue(off.isEmpty(), "$off")
        assertTrue("easymagic:enchanting_table" in MixinGate.REQUIRED_FOR_TARGET, "Easy Magic's table needs no mixin")
        assertTrue("enchantinginfuser:enchanting_infuser" in MixinGate.REQUIRED_FOR_TARGET, "the infuser needs no mixin")
    }

    /** The Stoker's handlers of Forbidden & Arcanus and Cobblemon Incubator write through their mixins (CD-FUEL-10, CD-FUEL-11). */
    @Test
    fun `CD-OPT-09 the Clibano and the Egg Incubator roles turn off without their mixins, with one warning each`() {
        val clibano = role("stoker_forbidden_arcanus", setOf("fire"), handler = "cobblemon_dynamics:clibano")
        val incubator = role("stoker_cobblemon_incubator", setOf("fire"), handler = "cobblemon_dynamics:egg_incubator")
        val warnings = mutableListOf<String>()
        val gated = MixinGate.apply(set(clibano, incubator, stoker), applied = { false }, warn = warnings::add)
        assertEquals(listOf(stoker.id), gated.active.map { it.definition.id })
        assertEquals(2, warnings.size, warnings.toString())
        assertTrue(warnings.any { "cobblemon_dynamics:clibano" in it && MixinState.CLIBANO in it }, warnings.toString())
        assertTrue(warnings.any { "cobblemon_dynamics:egg_incubator" in it && MixinState.EGG_INCUBATOR in it }, warnings.toString())
        val kept = MixinGate.apply(set(clibano, incubator), applied = { it == MixinState.CLIBANO || it == MixinState.EGG_INCUBATOR }) {
            error(it)
        }
        assertEquals(setOf(clibano.id, incubator.id), kept.active.map { it.definition.id }.toSet())
    }

    /** The Superheater's handler of Create Crafts & Additions raises the burner's heat through its mixin (CD-MCH-01). */
    @Test
    fun `CD-OPT-09 the Liquid Blaze Burner role turns off without its mixin, and the Blaze Burner's stays on`() {
        val liquid = role("superheater_createaddition", setOf("dragon"), priority = 10, handler = "cobblemon_dynamics:liquid_burner")
        val warnings = mutableListOf<String>()
        val gated = MixinGate.apply(set(liquid, superheater), applied = { it != MixinState.LIQUID_BLAZE_BURNER }, warn = warnings::add)
        assertEquals(listOf(superheater.id), gated.active.map { it.definition.id })
        assertTrue("cobblemon_dynamics:liquid_burner" in warnings.single() && MixinState.LIQUID_BLAZE_BURNER in warnings.single())
    }

    /** The Brewer's extra cook step runs in the cauldron's own tick, through its mixin, so without the mixin the role is off. */
    @Test
    fun `CD-OPT-09 CD-MCH-07 a missing cauldron mixin disables the Brewer of the Alchemist Cauldron with one warning`() {
        val cauldron = role("brewer_irons_spellbooks", setOf("poison"), handler = "cobblemon_dynamics:alchemist_cauldron")
        val warnings = mutableListOf<String>()
        val gated = MixinGate.apply(set(cauldron, stoker), applied = { it != MixinState.ALCHEMIST_CAULDRON }, warn = warnings::add)
        assertEquals(listOf(stoker.id), gated.active.map { it.definition.id })
        assertEquals(1, warnings.size)
        assertTrue("cobblemon_dynamics:alchemist_cauldron" in warnings.single() && MixinState.ALCHEMIST_CAULDRON in warnings.single())
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
