package codes.honor.cobblemondynamics.compattest

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.handler.tree.CellFacts
import codes.honor.cobblemondynamics.handler.tree.LumberjackHandler
import codes.honor.cobblemondynamics.handler.tree.LumberjackParams
import codes.honor.cobblemondynamics.handler.tree.TreeRules
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.test.TestKit
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.dtteam.dynamictrees.api.worldgen.LevelContext
import com.dtteam.dynamictrees.block.branch.BranchBlock
import com.dtteam.dynamictrees.config.DTConfigs
import com.dtteam.dynamictrees.entity.FallingTreeEntity
import com.dtteam.dynamictrees.tree.species.Species
import com.dtteam.dynamictrees.worldgen.DynamicTreeGenerationContext
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.BlockTags
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.entity.EntityTypeTest
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/**
 * The Lumberjack on real trees of Dynamic Trees 1.7.2 (CD-LUM-25, R89). Each test grows a Dynamic Trees oak on dirt from that mod's own
 * oak JoCode `JJJJOOP07+vU`, as `/dt settree` grows one: a trunk of 8 branches, a small crown, and the leaves of the species. The species'
 * bee nest feature can hang a nest on the tree, which would put the tree in the Lumberjack's never tag (CD-LUM-08), so the test removes
 * any nest. Dynamic Trees fells a tree in `BranchBlock.futureBreak` with a `FallingTreeEntity` of the destroy type HARVEST that starts at
 * the cut. A branch that loses its block through `setBlock`, as the Lumberjack's own fell removes blocks, makes Dynamic Trees drop the
 * rest of the tree by its sloppy break instead, with the destroy type VOID. With no hook a tree of Dynamic Trees is no tree; the compat
 * run always registers the hook, so `TreeRulesTest` covers that case.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class DynamicTreesCompatTests {
    /** The base branch of each oak, on the dirt that becomes its rooty soil. The pasture stands 6 blocks south of it. */
    private val base = BlockPos(11, 2, 9)

    /**
     * CD-LUM-25: a level 100 Scyther bound to the trunk of a Dynamic Trees oak fells it through `BranchBlock.futureBreak` at the base
     * cell, with the Scyther as the entity, and replants nothing. Before the bind, the base `dynamictrees:oak_branch` is a log of the
     * Lumberjack's target tag, in `#dynamictrees:branches`, of the namespace `dynamictrees`, and a `BranchBlock` by the hook, and the
     * Lumberjack's search finds the tree with every branch. When the base goes, one falling tree of the type HARVEST starts at the base
     * cell and no branch stands. For 60 ticks after the fell the base cell holds no sapling. Dynamic Trees' seeds plant themselves only
     * after 1,200 ticks, so a sapling in that time would be the Lumberjack's.
     *
     * The probe of the entity: `futureBreak` damages an axe in its entity's main hand by its `axeDamageMode`, by default THICKNESS:
     * max(1, r) ÷ 2 - 1 for a cut branch of radius r. The test gives the base branch the full radius of 8 through Dynamic Trees'
     * `setRadius`, and puts a diamond axe in the Scyther's main hand, so a damaged axe shows that the Scyther was the entity. In play a
     * Pokémon's hand is empty, and Dynamic Trees then applies neither Fortune nor axe damage.
     */
    @GameTest(template = "floor_23x17x23", timeoutTicks = 1_200)
    fun cdLum25LumberjackFellsADynamicTreesOakThroughFutureBreak(helper: GameTestHelper) {
        val branches = growOak(helper)
        checkBase(helper, OAK_BRANCH, inBranchTag = true)
        checkSearch(helper, branches)
        val pos = helper.absolutePos(base)
        val cut = helper.level.getBlockState(pos).block as BranchBlock
        cut.setRadius(helper.level, pos, FULL_RADIUS, null, Block.UPDATE_ALL)
        val radius = cut.getRadius(helper.level.getBlockState(pos))
        helper.assertTrue(radius == FULL_RADIUS, "the base branch has a radius of $radius, not $FULL_RADIUS")
        val (owner, pasture) = scene(helper)
        val scyther = TestKit.tether(helper, pasture, owner, "scyther level=100")
        scyther.setItemSlot(EquipmentSlot.MAINHAND, ItemStack(Items.DIAMOND_AXE))
        bind(helper, owner, scyther)
        var felledAt = -1L
        helper.onEachTick {
            if (felledAt < 0) {
                if (helper.getBlockState(base).block is BranchBlock) return@onEachTick
                felledAt = helper.tick
                checkFell(helper, branches)
                val axe = scyther.mainHandItem
                helper.assertTrue(
                    axe.`is`(Items.DIAMOND_AXE) && axe.damageValue > 0,
                    "CD-LUM-25: the Scyther's axe has ${axe.damageValue} damage after the fell, so futureBreak had another entity " +
                        "(axe damage mode ${DTConfigs.SERVER.axeDamageMode.get()})",
                )
            }
            checkNoReplant(helper, felledAt)
            if (helper.tick - felledAt >= 60) helper.succeed()
        }
    }

    /**
     * CD-LUM-25, R89: Dynamic Trees' own `stripBranch` strips every branch of the oak, as an axe strips them in play. The base is then
     * `dynamictrees:stripped_oak_branch`, a log of `#minecraft:logs` through `#minecraft:logs_that_burn` and so a target of the Lumberjack,
     * but outside `#dynamictrees:branches`: only its namespace `dynamictrees` and the hook's `BranchBlock` test mark it as a block of
     * Dynamic Trees. A level 100 Scyther bound to the trunk fells the tree through `futureBreak` all the same: one falling tree of the type
     * HARVEST starts at the base cell, no branch stands, and the base cell holds no sapling for 60 ticks.
     */
    @GameTest(template = "floor_23x17x23", timeoutTicks = 1_200)
    fun cdLum25StrippedDynamicTreesOakFallsThroughFutureBreak(helper: GameTestHelper) {
        val branches = growOak(helper)
        for (rel in branches) {
            val pos = helper.absolutePos(rel)
            val state = helper.level.getBlockState(pos)
            val branch = state.block as BranchBlock
            branch.stripBranch(state, helper.level, pos, branch.getRadius(state))
        }
        checkBase(helper, STRIPPED_OAK_BRANCH, inBranchTag = false)
        checkSearch(helper, branches)
        val (owner, pasture) = scene(helper)
        val scyther = TestKit.tether(helper, pasture, owner, "scyther level=100")
        bind(helper, owner, scyther)
        var felledAt = -1L
        helper.onEachTick {
            if (felledAt < 0) {
                if (helper.getBlockState(base).block is BranchBlock) return@onEachTick
                felledAt = helper.tick
                checkFell(helper, branches)
            }
            checkNoReplant(helper, felledAt)
            if (helper.tick - felledAt >= 60) helper.succeed()
        }
    }

    private fun scene(helper: GameTestHelper): Pair<ServerPlayer, PokemonPastureBlockEntity> {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(11, 1, 15), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        return owner to pasture
    }

    /** The Work Leash binds [scyther] to a trunk branch 2 above the base, and the binding names the base cell (CD-LUM-03). */
    private fun bind(helper: GameTestHelper, owner: ServerPlayer, scyther: PokemonEntity) {
        val binding = TestKit.bind(helper, owner, scyther, base.above(2))
        helper.assertTrue(binding?.roleId == id("lumberjack"), "the Scyther bound as ${binding?.roleId}")
        helper.assertTrue(binding?.target == helper.absolutePos(base), "CD-LUM-03: the binding names ${binding?.target}, not the base cell")
    }

    /** Grows the oak on dirt below [base], as `/dt settree` does on that cell, and returns the cells of its branches. */
    private fun growOak(helper: GameTestHelper): Set<BlockPos> {
        helper.setBlock(base.below(), Blocks.DIRT)
        val species = Species.findSpecies(OAK)
        if (species == null || species === Species.NULL_SPECIES) throw GameTestAssertException("Dynamic Trees has no species $OAK")
        val root = helper.absolutePos(base.below())
        val context = DynamicTreeGenerationContext(
            LevelContext.create(helper.level),
            species,
            root,
            root.mutable(),
            helper.level.getBiome(root),
            Direction.SOUTH,
            GENERATION_RADIUS,
            false,
        )
        species.getJoCode(OAK_CODE).setCareful(true).generate(context)
        for (cell in around()) if (helper.getBlockState(cell).`is`(Blocks.BEE_NEST)) helper.setBlock(cell, Blocks.AIR)
        val branches = around().filter { helper.getBlockState(it).block is BranchBlock }.toSet()
        helper.assertTrue(base in branches && branches.size > 1, "Dynamic Trees grew the branches $branches, not a tree on the base cell")
        return branches
    }

    /** The cells that the oak can take, by its JoCode and its leaves. */
    private fun around(): List<BlockPos> = BlockPos.betweenClosed(base.offset(-6, 0, -6), base.offset(6, 14, 6)).map { it.immutable() }

    /**
     * R89: what the Lumberjack reads of the real base block: a log of `#cobblemon_dynamics:lumberjack_targets` (CD-LUM-02), in
     * `#dynamictrees:branches` or not as [inBranchTag] says, a block of the namespace `dynamictrees` by `TreeRules.isDynamicTrees` with no
     * branch flag, and a `BranchBlock` by the hook that `compat/dynamictrees/` registers, which reports no vanilla log.
     */
    private fun checkBase(helper: GameTestHelper, expected: ResourceLocation, inBranchTag: Boolean) {
        val state = helper.getBlockState(base)
        val key = BuiltInRegistries.BLOCK.getKey(state.block)
        helper.assertTrue(key == expected, "the base cell holds $key, not $expected")
        helper.assertTrue(state.`is`(LUMBERJACK_TARGETS), "CD-LUM-02: $key is no log of #cobblemon_dynamics:lumberjack_targets")
        helper.assertTrue(
            state.`is`(DYNAMIC_BRANCHES) == inBranchTag,
            "R89: $key is ${if (inBranchTag) "not " else ""}in #dynamictrees:branches",
        )
        helper.assertTrue(
            TreeRules.isDynamicTrees(CellFacts(key)),
            "R89: TreeRules takes $key, by its namespace, for no block of Dynamic Trees",
        )
        val hook = LumberjackHandler.dynamicTrees
            ?: throw GameTestAssertException("CD-LUM-25: Dynamic Trees is loaded, and no fell hook is registered")
        helper.assertTrue(hook.isBranch(state), "R89: the hook reports $key as no BranchBlock")
        helper.assertTrue(!hook.isBranch(Blocks.OAK_LOG.defaultBlockState()), "R89: the hook reports the vanilla oak log as a BranchBlock")
    }

    /** CD-LUM-04 to CD-LUM-06 on the real tree: the Lumberjack's search finds the tree on the base cell, with every branch. */
    private fun checkSearch(helper: GameTestHelper, branches: Set<BlockPos>) {
        val plan = LumberjackHandler.plan(helper.level, helper.absolutePos(base), LumberjackParams.DEFAULT)
            ?: throw GameTestAssertException("CD-LUM-25: the Lumberjack finds no tree on the base of the Dynamic Trees oak")
        val planned = plan.steps.map { TestKit.relative(helper, it.pos) }.toSet()
        helper.assertTrue(planned.containsAll(branches), "the fell plan misses the branches at ${branches - planned}")
    }

    /**
     * CD-LUM-25: the tree fell by `BranchBlock.futureBreak` at the base cell. One falling tree of Dynamic Trees starts at that cut, with the
     * destroy type HARVEST, and no branch of the tree stands.
     */
    private fun checkFell(helper: GameTestHelper, branches: Set<BlockPos>) {
        val cut = helper.absolutePos(base)
        // Matched by its cut over the whole level, so the match never depends on the box that Dynamic Trees gives the entity.
        val falling = helper.level.getEntities(EntityTypeTest.forClass(FallingTreeEntity::class.java)) {
            it.getDestroyData()?.cutPos == cut
        }
        helper.assertTrue(falling.size == 1, "CD-LUM-25: ${falling.size} falling trees of Dynamic Trees start at the base cell, not 1")
        val type = falling.single().destroyType
        helper.assertTrue(type == FallingTreeEntity.DestroyType.HARVEST, "CD-LUM-25: the tree fell as $type, not as futureBreak's HARVEST")
        val standing = branches.filter { helper.getBlockState(it).block is BranchBlock }
        helper.assertTrue(standing.isEmpty(), "CD-LUM-25: the branches at $standing stand after the fell")
    }

    /** CD-LUM-25: the Lumberjack replants nothing after a fell of Dynamic Trees. `#minecraft:saplings` holds its saplings too. */
    private fun checkNoReplant(helper: GameTestHelper, felledAt: Long) {
        val state = helper.getBlockState(base)
        helper.assertTrue(
            !state.`is`(BlockTags.SAPLINGS),
            "CD-LUM-25: the base cell holds $state ${helper.tick - felledAt} ticks after the fell",
        )
    }

    private companion object {
        val OAK: ResourceLocation = ResourceLocation.fromNamespaceAndPath("dynamictrees", "oak")
        val OAK_BRANCH: ResourceLocation = ResourceLocation.fromNamespaceAndPath("dynamictrees", "oak_branch")
        val STRIPPED_OAK_BRANCH: ResourceLocation = ResourceLocation.fromNamespaceAndPath("dynamictrees", "stripped_oak_branch")

        /** Dynamic Trees' radius-2 oak JoCode from `trees/dynamictrees/jo_codes/oak.txt` in its 1.7.2 jar. */
        const val OAK_CODE = "JJJJOOP07+vU"

        /** The radius that `/dt settree` gives its generation context. */
        const val GENERATION_RADIUS = 8

        /** A branch of the full width of a block, the largest radius of the oak family. */
        const val FULL_RADIUS = 8

        val DYNAMIC_BRANCHES: TagKey<Block> = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("dynamictrees", "branches"),
        )
        val LUMBERJACK_TARGETS: TagKey<Block> = TagKey.create(Registries.BLOCK, id("lumberjack_targets"))
    }
}
