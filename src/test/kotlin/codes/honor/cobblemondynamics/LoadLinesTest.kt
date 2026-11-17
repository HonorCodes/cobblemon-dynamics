package codes.honor.cobblemondynamics

import codes.honor.cobblemondynamics.LoadLines.MixinEntry
import codes.honor.cobblemondynamics.LoadLines.MixinStatus
import codes.honor.cobblemondynamics.compat.AdapterNeeds
import codes.honor.cobblemondynamics.compat.CompatLines
import codes.honor.cobblemondynamics.compat.OptionalMod
import codes.honor.cobblemondynamics.role.BuiltInRoles
import codes.honor.cobblemondynamics.role.FakeHandler
import codes.honor.cobblemondynamics.role.RoleConditions
import codes.honor.cobblemondynamics.role.RoleSet
import com.google.gson.JsonParser
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LoadLinesTest {
    @Test
    fun `CD-CMP-08 the mixin line names each mixin under its heading, in name order, with each count`() {
        val applied = listOf(
            "vanilla.WorkerPushMixin",
            "create.AirCurrentMixin",
            "create.BlazeBurnerBlockEntityMixin",
            "create.ItemUseOverridesMixin",
            "create.WaterWheelBlockEntityMixin",
            "create.WindmillBearingBlockEntityMixin",
            "vanilla.EnchantmentMenuAccess",
            "vanilla.EnchantmentMenuMixin",
        ).map { MixinEntry(it, MixinStatus.APPLIED) }
        val cobblebase = listOf("PassiveXpMixin", "AmbientBehaviorMixin", "NavigationHelperMixin", "BaseManagerMixin").map {
            MixinEntry("compat.cobblebase.$it", MixinStatus.SKIPPED, "cobblebase absent")
        }
        val others = listOf(
            MixinEntry("test.ShadowMixin", MixinStatus.SKIPPED, "AirCurrent has no caughtEntities"),
            MixinEntry("test.MissedMixin", MixinStatus.NO_INJECTION),
            MixinEntry("cobblemon.client.PastureWidgetMixin", MixinStatus.NOT_APPLIED_YET),
            MixinEntry("cobblemon.client.PasturePokemonScrollListMixin", MixinStatus.NOT_APPLIED_YET),
        )
        val line = LoadLines.mixins(applied + cobblebase + others)
        val expected = "Mixins: 8 applied [create.AirCurrentMixin, create.BlazeBurnerBlockEntityMixin, create.ItemUseOverridesMixin, " +
            "create.WaterWheelBlockEntityMixin, create.WindmillBearingBlockEntityMixin, vanilla.EnchantmentMenuAccess, " +
            "vanilla.EnchantmentMenuMixin, vanilla.WorkerPushMixin], " +
            "5 skipped [compat.cobblebase.AmbientBehaviorMixin (cobblebase absent), " +
            "compat.cobblebase.BaseManagerMixin (cobblebase absent), compat.cobblebase.NavigationHelperMixin (cobblebase absent), " +
            "compat.cobblebase.PassiveXpMixin (cobblebase absent), test.ShadowMixin (AirCurrent has no caughtEntities)], " +
            "1 applied with no injection [test.MissedMixin], 2 not applied yet [cobblemon.client.PasturePokemonScrollListMixin, " +
            "cobblemon.client.PastureWidgetMixin]"
        assertEquals(expected, line)
    }

    @Test
    fun `CD-CMP-09 the payload line gives the version and each payload by direction`() {
        val toClient = listOf(id("pasture_panel_state"), id("leash_overview"))
        val toServer = listOf(id("work_mode_toggle"), id("pasture_panel_open"), id("pasture_panel_close"), id("leash_overview_request"))
        val expected =
            "Payloads, version 3: 2 to the client [cobblemon_dynamics:pasture_panel_state, cobblemon_dynamics:leash_overview], " +
                "4 to the server [cobblemon_dynamics:work_mode_toggle, cobblemon_dynamics:pasture_panel_open, " +
                "cobblemon_dynamics:pasture_panel_close, cobblemon_dynamics:leash_overview_request]"
        assertEquals(expected, LoadLines.payloads("3", toClient, toServer))
    }

    @Test
    fun `CD-CMP-10 the role line counts the built-in roles and names each disabled role with its reason`() {
        // A server without the optional mods of ADR-0016 skips each role file that its conditions name (CD-OPT-04).
        val files = BuiltInRoles.dir.listFiles { file -> file.extension == "json" }.orEmpty()
            .filterNot { JsonParser.parseString(it.readText()).asJsonObject.has(RoleConditions.KEY) }
        val roles = files.map { BuiltInRoles.parse(it) }.associateBy { it.id }
        assertEquals(21, roles.size)
        val create = setOf("water_wheel", "hand_crank", "burner_heat", "fan_catalyst", "tailwind").map(::id).toSet()
        val set = RoleSet(roles, { handler -> if (handler in create) null else FakeHandler(handler) }, setOf(id("muse"))) { }
        val line = LoadLines.roles(roles.size, set.active.size, set.disabled)
        assertTrue(line.startsWith("Roles: 21 loaded, 13 active, 8 disabled ["), line)
        assertTrue("cobblemon_dynamics:muse (roles.disabled)" in line, line)
        assertTrue("cobblemon_dynamics:cranker (handler cobblemon_dynamics:hand_crank is not registered)" in line, line)
        for (role in roles.values.filter { it.handler in create }) {
            assertTrue("${role.id} (handler ${role.handler} is not registered)" in line, line)
        }
    }

    @Test
    fun `CD-CRP-05 the farming line counts by adapter and names the unadapted blocks`() {
        assertEquals(
            "Farming: harvester 2 blocks [crop 1, pick_to_age_1 1], no adapter []",
            LoadLines.farming("harvester", mapOf("pick_to_age_1" to 1, "crop" to 1), emptyList()),
        )
        val wart = ResourceLocation.parse("minecraft:nether_wart")
        val beetroot = ResourceLocation.parse("minecraft:beetroots")
        assertEquals(
            "Farming: cultivator 4 blocks [growth 2], no adapter [minecraft:beetroots, minecraft:nether_wart]",
            LoadLines.farming("cultivator", mapOf("growth" to 2), listOf(wart, beetroot)),
        )
    }

    @Test
    fun `CD-OPT-04 the skipped line names each skipped role file with its mods, in id order`() {
        val skipped = mapOf(id("stoker_railcraft") to listOf("railcraft"), id("heater_mekanism") to listOf("mekanism"))
        assertEquals(
            "Roles not loaded, their mods are absent: 2 [cobblemon_dynamics:heater_mekanism (mekanism), " +
                "cobblemon_dynamics:stoker_railcraft (railcraft)]",
            LoadLines.skippedRoles(skipped),
        )
        assertEquals("Roles not loaded, their mods are absent: 0 []", LoadLines.skippedRoles(emptyMap()))
    }

    @Test
    fun `CD-OPT-04 a role file that a condition other than an absent mod skipped reads conditions`() {
        val skipped = mapOf(id("heater_mekanism") to listOf("mekanism"), id("stoker_without_create") to emptyList())
        assertEquals(
            "Roles not loaded, their mods are absent: 2 [cobblemon_dynamics:heater_mekanism (mekanism), " +
                "cobblemon_dynamics:stoker_without_create (conditions)]",
            LoadLines.skippedRoles(skipped),
        )
    }

    @Test
    fun `CD-OPT-11 the compat line names each adapter as found, or missing with what is missing`() {
        val ie = OptionalMod("immersiveengineering", listOf("blusunrize/"), listOf("ie_windmill", "ie_furnace", "ie_watermill"))
        val windmill = "compat.immersiveengineering.WindmillMixin"
        val needs = mapOf(
            "ie_furnace" to AdapterNeeds(blocks = listOf(ResourceLocation.parse("immersiveengineering:cokeoven"))),
            "ie_watermill" to AdapterNeeds(blocks = listOf(ResourceLocation.parse("immersiveengineering:watermill"))),
            "ie_windmill" to AdapterNeeds(mixins = listOf(windmill)),
        )
        assertEquals(
            "Compat: immersiveengineering 12.4.2-194 [ie_furnace found, ie_watermill found, ie_windmill missing (mixin)]",
            CompatLines.line(ie, "12.4.2-194", needs::get, blockExists = { true }, applied = { it != windmill }),
        )
        // A block id that does not resolve, and an adapter that its compat package never registered, as its class did not load.
        val lost = ResourceLocation.parse("immersiveengineering:watermill")
        val registered = { adapter: String -> if (adapter == "ie_furnace") null else needs[adapter] }
        assertEquals(
            "Compat: immersiveengineering 12.4.2-194 [ie_furnace missing (class), ie_watermill missing (block), ie_windmill found]",
            CompatLines.line(ie, "12.4.2-194", registered, blockExists = { it != lost }, applied = { true }),
        )
        val aether = OptionalMod("aether", listOf("com/aetherteam/"), compat = null)
        assertEquals("Compat: aether 1.5.10 []", CompatLines.line(aether, "1.5.10", { null }, { true }, { true }))
    }
}
