package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.bed.BedBlock
import codes.honor.cobblemondynamics.bed.BedBlockEntity
import codes.honor.cobblemondynamics.id
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.item.crafting.RecipeType
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/** The four beds as the game registers and loads them: the block registry, the block entities and the loaded recipes. */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class BedContentTests {
    private fun item(id: String): Item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id))

    /** The block registry holds exactly four beds of the mod, with these ids, and each placed bed's block entity names its tier (CD-BED-01). */
    @GameTest(template = "floor_9x5x9")
    fun cdBed01RegistryListsFourBedsWithTheirTiers(helper: GameTestHelper) {
        val expected = mapOf(id("hay_bed") to 1, id("wool_bed") to 2, id("plush_bed") to 3, id("starlit_bed") to 4)
        val registered = BuiltInRegistries.BLOCK.entrySet().filter { (_, block) -> block is BedBlock }.map { it.key.location() }
        helper.assertTrue(registered.toSet() == expected.keys, "CD-BED-01: the registry's beds are $registered")
        for ((index, entry) in expected.entries.withIndex()) {
            val (blockId, tier) = entry
            val block = BuiltInRegistries.BLOCK.getOptional(blockId).orElse(null)
            helper.assertTrue(block is BedBlock, "CD-BED-01: $blockId is ${block?.javaClass?.simpleName}, not a bed")
            val rel = BlockPos(1 + 2 * index, 2, 4)
            helper.setBlock(rel, block!!)
            val placed = helper.getBlockEntity<BedBlockEntity>(rel)
            helper.assertTrue(placed.tier.tier == tier, "CD-BED-01: the placed $blockId reports tier ${placed.tier.tier}, not $tier")
        }
        helper.succeed()
    }

    /**
     * Each bed comes out of its exact inputs through the loaded recipes, and each upgrade needs the bed below it: the same grid with
     * that bed replaced by air (shaped) or left out (shapeless) gives no recipe (CD-BED-02).
     */
    @GameTest(template = "floor_9x5x9")
    fun cdBed02EachBedCraftsFromTheBedBelow(helper: GameTestHelper) {
        val hay = item("cobblemon_dynamics:hay_bed")
        val wool = item("cobblemon_dynamics:wool_bed")
        val plush = item("cobblemon_dynamics:plush_bed")
        val starlit = item("cobblemon_dynamics:starlit_bed")
        val candy = item("cobblemon:rare_candy")
        helper.assertTrue(candy != Items.AIR, "Cobblemon registers no cobblemon:rare_candy")
        val air = Items.AIR
        val hayGrid = listOf(Items.HAY_BLOCK, Items.HAY_BLOCK, Items.HAY_BLOCK) + List(3) { Items.OAK_PLANKS } + List(3) { air }
        val iron = Items.IRON_INGOT
        val woolItem = Items.WHITE_WOOL
        val woolGrid = listOf(iron, woolItem, iron, woolItem, hay, woolItem, iron, woolItem, iron)
        val diamond = Items.DIAMOND
        val gold = Items.GOLD_INGOT
        val plushGrid = listOf(diamond, gold, diamond, gold, wool, gold, diamond, gold, diamond)
        val starlitGrid = listOf(plush, Items.NETHER_STAR, candy, candy, candy, candy, air, air, air)
        assertCrafts(helper, hayGrid, hay, "the Hay Bed")
        assertCrafts(helper, woolGrid, wool, "the Wool Bed")
        assertCrafts(helper, plushGrid, plush, "the Plush Bed")
        assertCrafts(helper, starlitGrid, starlit, "the Starlit Bed")
        assertCrafts(helper, woolGrid.map { if (it == hay) air else it }, null, "the Wool Bed grid with air for the Hay Bed")
        assertCrafts(helper, plushGrid.map { if (it == wool) air else it }, null, "the Plush Bed grid with air for the Wool Bed")
        assertCrafts(helper, starlitGrid.map { if (it == plush) air else it }, null, "the Starlit Bed inputs without the Plush Bed")
        helper.succeed()
    }

    /** [grid] in a 3 × 3 crafting grid gives one [expected], or nothing when [expected] is null. */
    private fun assertCrafts(helper: GameTestHelper, grid: List<Item>, expected: Item?, what: String) {
        val level = helper.level
        val input = CraftingInput.of(3, 3, grid.map { if (it == Items.AIR) ItemStack.EMPTY else ItemStack(it) })
        val result = level.recipeManager.getRecipeFor(RecipeType.CRAFTING, input, level)
            .map { it.value().assemble(input, level.registryAccess()) }
            .orElse(ItemStack.EMPTY)
        if (expected == null) {
            helper.assertTrue(result.isEmpty, "CD-BED-02: $what makes $result")
        } else {
            helper.assertTrue(result.`is`(expected) && result.count == 1, "CD-BED-02: $what makes $result")
        }
    }
}
