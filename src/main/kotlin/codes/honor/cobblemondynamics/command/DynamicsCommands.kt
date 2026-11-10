package codes.honor.cobblemondynamics.command

import codes.honor.cobblemondynamics.claim.TargetKey
import codes.honor.cobblemondynamics.engine.WorkEngine
import codes.honor.cobblemondynamics.registry.DynamicsAttachments
import com.cobblemon.mod.common.Cobblemon
import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import java.util.UUID

/** The operator commands under `/cobblemondynamics`. */
object DynamicsCommands {
    private const val PERMISSION_OPERATOR = 2
    private const val CLAIM = "cobblemon_dynamics.command.claims.claim"
    private const val BINDING = "cobblemon_dynamics.command.claims.binding"
    private const val NONE = "cobblemon_dynamics.command.claims.none"
    private const val KIND = "cobblemon_dynamics.command.claims.kind"

    fun register() {
        NeoForge.EVENT_BUS.addListener<RegisterCommandsEvent> { register(it.dispatcher) }
    }

    /** CD-SEC-09: the root literal needs permission level 2, so no subcommand runs for a player below it. */
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("cobblemondynamics")
                .requires { it.hasPermission(PERMISSION_OPERATOR) }
                .then(Commands.literal("claims").executes { claims(it.source) }),
        )
    }

    /** CD-UI-14: one line per claim and per leash binding, with its target, its worker and, for a claim, its kind. Returns the count. */
    private fun claims(source: CommandSourceStack): Int {
        val server = source.server
        val engine = WorkEngine.get()
        val bindings = server.allLevels.flatMap { level ->
            val dimension = level.dimension().location()
            val index = level.getExistingDataOrNull(DynamicsAttachments.LEASH_INDEX)?.all().orEmpty()
            index.map { (pos, ref) -> TargetKey.block(dimension, pos) to ref }
        }
        val lines = ClaimsReport.lines(engine?.claims?.all().orEmpty(), bindings)
        if (lines.isEmpty()) source.sendSuccess({ Component.translatable(NONE) }, false)
        for (line in lines) source.sendSuccess({ render(server, engine, line) }, false)
        return lines.size
    }

    private fun render(server: MinecraftServer, engine: WorkEngine?, line: ClaimsReport.Line): Component = when (line) {
        is ClaimsReport.Line.ClaimLine -> {
            val claim = line.claim
            val kind = Component.translatable("$KIND.${claim.kind.name.lowercase()}")
            val target: Any = when (val key = claim.key) {
                is TargetKey.Block -> ClaimsReport.blockText(key)
                is TargetKey.Entity -> server.allLevels.firstNotNullOfOrNull { it.getEntity(key.uuid) }?.name ?: key.uuid.toString()
            }
            Component.translatable(CLAIM, kind, target, nameOf(server, engine, claim.worker, null), claim.worker.toString())
        }

        is ClaimsReport.Line.BindingLine -> {
            val name = nameOf(server, engine, line.ref.pokemonId, line.ref.owner)
            Component.translatable(BINDING, ClaimsReport.blockText(line.target), name, line.ref.pokemonId.toString())
        }
    }

    /** The worker's display name: from the engine, else from its owner's PC, else its UUID. */
    private fun nameOf(server: MinecraftServer, engine: WorkEngine?, pokemonId: UUID, owner: UUID?): Any {
        val pokemon = engine?.pokemonOf(pokemonId) ?: owner?.let { Cobblemon.storage.getPC(it, server.registryAccess())[pokemonId] }
        return pokemon?.getDisplayName() ?: pokemonId.toString()
    }
}
