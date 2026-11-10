package codes.honor.cobblemondynamics.coretest

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.role.RoleRegistry
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import java.nio.file.Files

/** A server with Cobblemon and KotlinForForge only: the mod loads, and the roles that need Create are off. */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class CoreTests {
    /** The roles that only Create's handlers serve. */
    private val createRoles = listOf("wheel_driver", "burner_keeper", "superheater", "cranker")

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

    @GameTest(template = "floor_9x5x9")
    fun cdPlat05LoadsWithoutCreateAndAe2(helper: GameTestHelper) {
        for (modId in listOf("create", "ae2")) helper.assertFalse(ModList.get().isLoaded(modId), "$modId is loaded in the core run")
        helper.assertTrue(RoleRegistry.current.get(id("stoker")) != null, "the Stoker role is not active")
        helper.succeed()
    }
}
