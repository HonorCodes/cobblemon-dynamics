package codes.honor.cobblemondynamics.coretest

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.role.RoleRegistry
import codes.honor.cobblemondynamics.test.TestKit
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Blocks
import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import java.nio.file.Files

/**
 * A server with Cobblemon and KotlinForForge only: the mod loads, the roles that need Create are off, the item roles are on, and the
 * Crystal Tender works.
 */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class CoreTests {
    /** The roles that only Create's handlers serve. */
    private val createRoles = listOf("wheel_driver", "superheater", "cranker")

    /**
     * Each Create role whose file is in the data logs exactly one line, at the data load, and none of them is active. A role file
     * that a later task adds is counted from then on.
     */
    @GameTest(template = "floor_9x5x9")
    fun cdCfg10CreateRolesOffWithoutCreate(helper: GameTestHelper) {
        val log = String(Files.readAllBytes(FMLPaths.GAMEDIR.get().resolve("logs/latest.log")), Charsets.UTF_8).lines()
        val resources = helper.level.server.resourceManager
        for (name in createRoles) {
            val role = id(name)
            helper.assertTrue(RoleRegistry.current.get(role) == null, "the Create role $role is active without Create")
            val file = ResourceLocation.fromNamespaceAndPath(MOD_ID, "cobblemon_dynamics/roles/$name.json")
            val expected = if (resources.getResource(file).isPresent) 1 else 0
            val lines = log.count { "Role $role is disabled: handler" in it }
            helper.assertTrue(lines == expected, "the log holds $lines lines that disable $role, expected $expected")
        }
        helper.succeed()
    }

    /** The Crystal Tender's tag comes from NeoForge, so the role needs no AE2 (CD-CRY-04). */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 600)
    fun cdCry04CrystalTenderWithoutAe2(helper: GameTestHelper) {
        helper.assertFalse(ModList.get().isLoaded("ae2"), "AE2 is loaded in the core run")
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        val budding = BlockPos(7, 1, 3)
        helper.setBlock(budding, Blocks.BUDDING_AMETHYST)
        val tender = TestKit.tether(helper, pasture, owner, "rockruff level=20")
        val key = TestKit.keyOf(helper, helper.absolutePos(budding))
        helper.succeedWhen {
            val role = RoleRegistry.current.get(id("crystal_tender"))
            helper.assertTrue(role?.handler?.id == id("budding"), "the Crystal Tender role is not active: $role")
            val claim = TestKit.claimOf(helper, tender)
            helper.assertTrue(claim?.kind == ClaimKind.WORK && claim.key == key, "the Rockruff holds $claim, not the budding amethyst")
        }
    }

    /** Telekinesis and the Magnet name no Create class, so they work in a pack without Create (CD-PLAT-05, CD-CFG-10). */
    @GameTest(template = "floor_9x5x9")
    fun cdCfg10ItemRolesNeedNoCreate(helper: GameTestHelper) {
        for (name in listOf("telekinesis", "magnet")) {
            helper.assertTrue(RoleRegistry.current.get(id(name)) != null, "$name is off without Create")
        }
        helper.succeed()
    }

    @GameTest(template = "floor_9x5x9")
    fun cdPlat05LoadsWithoutCreateAndAe2(helper: GameTestHelper) {
        for (modId in listOf("create", "ae2")) helper.assertFalse(ModList.get().isLoaded(modId), "$modId is loaded in the core run")
        helper.assertTrue(RoleRegistry.current.get(id("stoker")) != null, "the Stoker role is not active")
        helper.succeed()
    }
}
