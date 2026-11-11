package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.registry.DynamicsItems
import codes.honor.cobblemondynamics.registry.DynamicsTabs
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.CreativeModeTabs
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/** The mod's creative tab, built on the server as the client's creative inventory builds it (the approver's ruling, 2026-11-10). */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class CreativeTabTests {
    /** The tab of [key] after `buildContents`, which also fires NeoForge's `BuildCreativeModeTabContentsEvent` for it. */
    private fun built(helper: GameTestHelper, key: ResourceKey<CreativeModeTab>): CreativeModeTab {
        val tab = BuiltInRegistries.CREATIVE_MODE_TAB.getOrThrow(key)
        tab.buildContents(CreativeModeTab.ItemDisplayParameters(helper.level.enabledFeatures(), true, helper.level.registryAccess()))
        return tab
    }

    private fun idsOf(tab: CreativeModeTab): List<ResourceLocation> = tab.displayItems.map { BuiltInRegistries.ITEM.getKey(it.item) }

    /**
     * CD-UI-16: the tab `cobblemon_dynamics:main` is titled "Cobblemon Dynamics", shows the Work Leash as its icon, and lists every
     * item that the mod registers, in this order: the Work Leash, the four beds by tier and the Charge Cell. The vanilla tabs that
     * listed the items before list none of them now.
     */
    @GameTest(template = "floor_9x5x9")
    fun cdUi16CreativeTabListsEveryItemOfTheMod(helper: GameTestHelper) {
        val key = ResourceKey.create(Registries.CREATIVE_MODE_TAB, id("main"))
        helper.assertTrue(BuiltInRegistries.CREATIVE_MODE_TAB.get(key) === DynamicsTabs.MAIN.get(), "CD-UI-16: no tab $key")
        val tab = built(helper, key)
        helper.assertTrue(tab.displayName.string == "Cobblemon Dynamics", "CD-UI-16: the tab is titled '${tab.displayName.string}'")
        helper.assertTrue(tab.iconItem.`is`(DynamicsItems.WORK_LEASH.get()), "CD-UI-16: the tab's icon is ${tab.iconItem}")
        val listed = idsOf(tab)
        val expected = listOf("work_leash", "hay_bed", "wool_bed", "plush_bed", "starlit_bed", "charge_cell").map(::id)
        helper.assertTrue(listed == expected, "CD-UI-16: the tab lists $listed")
        val registered = BuiltInRegistries.ITEM.keySet().filter { it.namespace == MOD_ID }.toSet()
        helper.assertTrue(registered == listed.toSet(), "CD-UI-16: the mod registers $registered, and the tab lists $listed")
        for (vanilla in listOf(CreativeModeTabs.TOOLS_AND_UTILITIES, CreativeModeTabs.FUNCTIONAL_BLOCKS)) {
            val ours = idsOf(built(helper, vanilla)).filter { it.namespace == MOD_ID }
            helper.assertTrue(ours.isEmpty(), "CD-UI-16: the vanilla tab ${vanilla.location()} lists $ours")
        }
        helper.succeed()
    }
}
