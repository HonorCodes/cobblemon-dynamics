package codes.honor.cobblemondynamics

import codes.honor.cobblemondynamics.handler.farming.HarvestLoot
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FarmingLinesTest {
    private fun block(id: String) = ResourceKey.create(Registries.BLOCK, ResourceLocation.parse(id))

    private fun table(id: String) = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(id))

    @Test
    fun `CD-FRM-03 the Harvester's line names each entry of the loot data map whose table is not loaded`() {
        val blackberries = table("twilightforest:blocks/blackberry_bush_berries")
        val typo = table("twilightforest:blocks/blueberry_bush_berriez")
        val entries = mapOf(
            block("twilightforest:blackberry_bush") to HarvestLoot(blackberries, 2),
            block("twilightforest:blueberry_bush") to HarvestLoot(typo, 2),
        )
        val unknown = FarmingLines.unknownTables(entries) { it == blackberries }
        assertEquals(mapOf(ResourceLocation.parse("twilightforest:blueberry_bush") to typo.location()), unknown)
        assertEquals(
            "Farming: harvester 2 blocks [pick_by_loot_table 2], no adapter [], " +
                "unknown loot table [twilightforest:blueberry_bush (twilightforest:blocks/blueberry_bush_berriez)]",
            LoadLines.farming("harvester", mapOf("pick_by_loot_table" to 2), emptyList(), unknown),
        )
        // CD-CRP-05: with every table loaded, the line keeps its form.
        assertEquals(
            "Farming: harvester 2 blocks [crop 1, pick_to_age_1 1], no adapter []",
            LoadLines.farming("harvester", mapOf("pick_to_age_1" to 1, "crop" to 1), emptyList(), emptyMap()),
        )
    }

    @Test
    fun `CD-PCK-05 the Picker's line names each member of its tag that is no apricorn`() {
        val id = { s: String -> ResourceLocation.parse(s) }
        assertEquals(
            "Farming: picker 11 blocks [apricorn 7], not apricorns [a:fig, b:plum], no age [c:leaf], refused [minecraft:kelp]",
            LoadLines.picker(7, listOf(id("b:plum"), id("a:fig")), listOf(id("c:leaf")), listOf(id("minecraft:kelp"))),
        )
        assertEquals(
            "Farming: picker 7 blocks [apricorn 7], not apricorns [], no age [], refused []",
            LoadLines.picker(7, emptyList(), emptyList(), emptyList()),
        )
    }
}
