package codes.honor.cobblemondynamics.net

import codes.honor.cobblemondynamics.engine.WorkEngine
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.pasture.PastureAdapter
import codes.honor.cobblemondynamics.pasture.PastureKey
import codes.honor.cobblemondynamics.pasture.WorkModePolicy
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import java.util.UUID

/** The players who have a pasture's panel open, and the last state that each was sent. Server thread only. */
object PanelViewers {
    private const val PERMISSION_OPERATOR = 2

    /** Until Work Stamina exists, every Pokémon of the list reads full. */
    private const val FULL_PERCENT = 100

    private val viewers = HashMap<PastureKey, LinkedHashSet<ServerPlayer>>()
    private val lastSent = HashMap<UUID, PasturePanelStatePayload>()

    /** [player] opened the panel of the pasture at [pos]: it views that pasture alone and gets its state now. */
    fun open(player: ServerPlayer, level: ServerLevel, pos: BlockPos) {
        close(player)
        viewers.getOrPut(keyOf(level, pos), ::LinkedHashSet) += player
        send(player, level, pos)
    }

    fun close(player: ServerPlayer) {
        viewers.values.forEach { it.remove(player) }
        viewers.values.removeIf { it.isEmpty() }
    }

    /** CD-SEC-06: after every Work Mode change, in arrival order, each viewer gets the state that the change left. */
    fun broadcast(level: ServerLevel, pos: BlockPos) {
        viewers[keyOf(level, pos)]?.toList()?.forEach { send(it, level, pos) }
    }

    /** Sends [player] the state of the pasture at [pos], for example to correct a panel after a refused request. */
    fun send(player: ServerPlayer, level: ServerLevel, pos: BlockPos) {
        val be = PastureAdapter.pastureAt(level, pos) ?: return
        val payload = stateFor(player, be)
        lastSent[player.uuid] = payload
        PacketDistributor.sendToPlayer(player, payload)
    }

    /** The last state sent to [playerId]. Fake players drop every packet, so the GameTests read this. */
    fun lastSent(playerId: UUID): PasturePanelStatePayload? = lastSent[playerId]

    fun forget(player: ServerPlayer) {
        close(player)
        lastSent.remove(player.uuid)
    }

    fun reset() {
        viewers.clear()
        lastSent.clear()
    }

    private fun stateFor(player: ServerPlayer, be: PokemonPastureBlockEntity): PasturePanelStatePayload {
        val engine = WorkEngine.get()
        val canToggle = WorkModePolicy.mayToggle(player.uuid, player.hasPermissions(PERMISSION_OPERATOR), PastureAdapter.placer(be))
        val entries = PastureAdapter.tetheredIds(be).map { id ->
            PanelEntry(id, FULL_PERCENT, engine?.worker(id)?.status ?: WorkerStatus.IDLE)
        }
        return PasturePanelStatePayload(PastureAdapter.isWorkMode(be), canToggle, entries)
    }

    private fun keyOf(level: ServerLevel, pos: BlockPos) = PastureKey(level.dimension().location(), pos.asLong())
}
