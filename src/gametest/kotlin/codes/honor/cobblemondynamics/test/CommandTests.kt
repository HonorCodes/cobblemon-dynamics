package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import net.minecraft.commands.CommandSource
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

/** The `/cobblemondynamics` commands as players run them, through `Commands.performPrefixedCommand`. */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class CommandTests {
    /** Takes what a command sends to its source. A fake player drops it. */
    private class Output : CommandSource {
        val lines = mutableListOf<String>()

        override fun sendSystemMessage(component: Component) {
            lines += component.string
        }

        override fun acceptsSuccess(): Boolean = true

        override fun acceptsFailure(): Boolean = true

        override fun shouldInformAdmins(): Boolean = false
    }

    private class Result(val lines: List<String>, val ran: Boolean)

    /** Runs [command] as [player], with the player's own permission level, and keeps what it answered and whether it ran. */
    private fun run(player: ServerPlayer, command: String): Result {
        val output = Output()
        var ran = false
        val source = player.createCommandSourceStack().withSource(output).withCallback { success, _ -> if (success) ran = true }
        player.server.commands.performPrefixedCommand(source, command)
        return Result(output.lines, ran)
    }

    /**
     * A Charmander works a furnace and a Vulpix is bound to another. An operator's `/cobblemondynamics claims` lists the Charmander's
     * work claim with its kind, target and worker, and the Vulpix's binding with its target and worker (CD-UI-14, the work claim and
     * binding part). Other tests' entries may sit between them.
     */
    @GameTest(template = "floor_15x6x15", timeoutTicks = 600)
    fun cdUi14ClaimsListsTheWorkClaimAndTheBinding(helper: GameTestHelper) {
        val owner = TestKit.owner(helper)
        val pasture = TestKit.placePasture(helper, BlockPos(7, 1, 7), owner)
        TestKit.workMode(pasture, true)
        TestKit.standBeside(owner, pasture)
        val worked = TestKit.furnace(helper, BlockPos(3, 1, 3), ItemStack(Items.RAW_IRON, 8)).blockPos
        val boundRel = BlockPos(11, 1, 3)
        val bound = TestKit.furnace(helper, boundRel, ItemStack(Items.RAW_IRON, 8)).blockPos
        val stoker = TestKit.tether(helper, pasture, owner, "charmander level=10")
        val vulpix = TestKit.tether(helper, pasture, owner, "vulpix level=10")
        helper.assertTrue(TestKit.bind(helper, owner, vulpix, boundRel) != null, "the Vulpix did not bind")
        val operator = TestKit.operator(helper)
        val dimension = helper.level.dimension().location()
        val claimLine = "claim work $dimension ${worked.x} ${worked.y} ${worked.z} Charmander (${stoker.pokemon.uuid})"
        val bindingLine = "binding $dimension ${bound.x} ${bound.y} ${bound.z} Vulpix (${vulpix.pokemon.uuid})"
        helper.succeedWhen {
            val working = TestKit.claimOf(helper, stoker)?.key == TestKit.keyOf(helper, worked)
            helper.assertTrue(working, "the Charmander holds no claim on its furnace")
            val result = run(operator, "cobblemondynamics claims")
            helper.assertTrue(result.ran, "the operator's claims command did not run: ${result.lines}")
            helper.assertTrue(claimLine in result.lines, "CD-UI-14: the output lacks '$claimLine': ${result.lines}")
            helper.assertTrue(bindingLine in result.lines, "CD-UI-14: the output lacks '$bindingLine': ${result.lines}")
        }
    }

    /**
     * A player at permission level 0 runs both commands, and neither runs: the reply is Minecraft's unknown-command error, and it holds no
     * line of either command. An operator runs the same two commands, and both run (CD-SEC-09).
     */
    @GameTest(template = "floor_9x5x9", timeoutTicks = 100)
    fun cdSec09LevelZeroPlayerIsRefused(helper: GameTestHelper) {
        val player = TestKit.owner(helper, "player")
        helper.assertFalse(player.hasPermissions(1), "the test player has a permission level above 0")
        val wild = TestKit.wild(helper, BlockPos(4, 2, 4), "charmander level=10")
        val operator = TestKit.operator(helper)
        for (command in listOf("cobblemondynamics claims", "cobblemondynamics stamina ${wild.stringUUID}")) {
            val refused = run(player, command)
            helper.assertFalse(refused.ran, "CD-SEC-09: '$command' ran for a level-0 player: ${refused.lines}")
            val leaked = refused.lines.filter { line -> COMMAND_OUTPUT.any { it in line } }
            helper.assertTrue(refused.lines.isNotEmpty() && leaked.isEmpty(), "CD-SEC-09: a level-0 '$command' answered ${refused.lines}")
            val allowed = run(operator, command)
            helper.assertTrue(allowed.ran && allowed.lines.isNotEmpty(), "the operator's '$command' did not run: ${allowed.lines}")
        }
        helper.succeed()
    }

    private companion object {
        /** Text that only the output of the claims or stamina command holds. */
        val COMMAND_OUTPUT = listOf("Work Stamina", "No claims and no leash bindings", "claim work", "claim bed", "claim heal", "binding ")
    }
}
