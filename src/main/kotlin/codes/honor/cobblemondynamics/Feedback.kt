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

    /** The chat messages of players who were offline when they were sent, such as a cleared binding's (CD-LSH-13). */
    private val offline = OfflineMessages<Component>()

    fun actionBar(player: ServerPlayer, message: Component) {
        record(player.uuid, message)
        player.displayClientMessage(message, true)
    }

    /** To a player who may be offline, such as the owner of a worker. */
    fun actionBar(playerId: UUID, server: MinecraftServer, message: Component) {
        record(playerId, message)
        server.playerList.getPlayer(playerId)?.displayClientMessage(message, true)
    }

    /** To a player who may be offline. An offline player gets it at the next login if it is among the 10 newest that waited. */
    fun chat(playerId: UUID, server: MinecraftServer, message: Component) {
        record(playerId, message)
        val player = server.playerList.getPlayer(playerId)
        if (player != null) player.sendSystemMessage(message) else offline.add(playerId, message)
    }

    /** Sends [player] the chat messages that waited for this login, oldest first. */
    fun onLogin(player: ServerPlayer) {
        for (message in offline.take(player.uuid)) player.sendSystemMessage(message)
    }

    /** Drops the waiting messages when a server stops: they live in memory only. */
    fun reset() {
        offline.clear()
    }

    private fun record(playerId: UUID, message: Component) {
        if (recording) recorded.getOrPut(playerId, ::ArrayList) += message
    }
}
