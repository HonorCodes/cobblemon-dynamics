package codes.honor.cobblemondynamics

import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/** Every message that the mod sends a player goes through here. Server thread only. */
object Feedback {
    /** When true, every message is kept in [recorded] by player UUID. The GameTests turn it on, because fake players drop messages. */
    var recording: Boolean = false

    val recorded: MutableMap<UUID, MutableList<Component>> = HashMap()

    fun actionBar(player: ServerPlayer, message: Component) {
        record(player.uuid, message)
        player.displayClientMessage(message, true)
    }

    /** To a player who may be offline, such as the owner of a worker. */
    fun actionBar(playerId: UUID, server: MinecraftServer, message: Component) {
        record(playerId, message)
        server.playerList.getPlayer(playerId)?.displayClientMessage(message, true)
    }

    fun chat(playerId: UUID, server: MinecraftServer, message: Component) {
        record(playerId, message)
        server.playerList.getPlayer(playerId)?.sendSystemMessage(message)
    }

    private fun record(playerId: UUID, message: Component) {
        if (recording) recorded.getOrPut(playerId, ::ArrayList) += message
    }
}
