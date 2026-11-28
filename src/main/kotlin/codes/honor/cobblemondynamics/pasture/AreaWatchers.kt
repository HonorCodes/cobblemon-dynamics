package codes.honor.cobblemondynamics.pasture

import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.net.AreaBoundsPayload
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.PacketDistributor
import java.util.UUID

object AreaWatchers {
    const val MAX_WATCH_DISTANCE = 64.0
    const val MAX_WATCHES_PER_PLAYER = 16
    private const val MAX_HISTORY = 32

    private val byPlayer = HashMap<UUID, LinkedHashMap<PastureKey, ServerPlayer>>()
    private val lastSent = HashMap<UUID, AreaBoundsPayload>()
    private val history = HashMap<UUID, ArrayDeque<AreaBoundsPayload>>()

    fun onWatch(player: ServerPlayer, pos: BlockPos, watching: Boolean) {
        val level = player.serverLevel()
        val key = PastureKey(level.dimension().location(), pos.asLong())
        if (!watching) {
            removeWatch(player.uuid, key)
            return
        }
        val inRange = player.position().distanceTo(Vec3.atCenterOf(pos)) <= MAX_WATCH_DISTANCE
        val be = if (inRange) PastureAdapter.pastureAt(level, pos) else null
        if (be == null || !PastureAdapter.isWorkMode(be)) {
            removeWatch(player.uuid, key)
            send(player, AreaBoundsPayload.inactive(pos))
            return
        }
        val targetPos = be.blockPos.immutable()
        val targetKey = PastureKey(level.dimension().location(), targetPos.asLong())
        val watches = byPlayer.getOrPut(player.uuid) { LinkedHashMap() }
        if (targetKey !in watches && watches.size >= MAX_WATCHES_PER_PLAYER) {
            val oldest = watches.keys.first()
            watches.remove(oldest)
            send(player, AreaBoundsPayload.inactive(BlockPos.of(oldest.pos)))
        }
        watches[targetKey] = player
        val area = PastureAdapter.workArea(level, targetPos, WorkEngineSettings.current)
        send(player, AreaBoundsPayload.active(targetPos, area))
    }

    fun isWatching(playerId: UUID, level: ServerLevel, pos: BlockPos): Boolean =
        byPlayer[playerId]?.containsKey(PastureKey(level.dimension().location(), pos.asLong())) == true

    fun broadcast(level: ServerLevel, pos: BlockPos) {
        val be = PastureAdapter.pastureAt(level, pos)
        if (be == null || !PastureAdapter.isWorkMode(be)) {
            deactivate(level, pos)
            return
        }
        val key = PastureKey(level.dimension().location(), be.blockPos.asLong())
        val area = PastureAdapter.workArea(level, be.blockPos, WorkEngineSettings.current)
        val payload = AreaBoundsPayload.active(be.blockPos, area)
        for (player in watchersOf(level, key)) {
            send(player, payload)
        }
    }

    fun deactivate(level: ServerLevel, pos: BlockPos) {
        val key = PastureKey(level.dimension().location(), pos.asLong())
        val watchers = watchersOf(level, key)
        removePasture(key)
        val payload = AreaBoundsPayload.inactive(pos)
        for (player in watchers) {
            send(player, payload)
        }
    }

    fun refreshAll() {
        val pairs = ArrayList<Pair<ServerLevel, BlockPos>>()
        for (watches in byPlayer.values.toList()) {
            for ((key, player) in watches.entries.toList()) {
                if (player.isRemoved || player.serverLevel().dimension().location() != key.dimension) continue
                pairs += player.serverLevel() to BlockPos.of(key.pos)
            }
        }
        for ((level, pos) in pairs.distinctBy { it.first.dimension().location() to it.second.asLong() }) {
            broadcast(level, pos)
        }
    }

    fun onDimensionChanged(player: ServerPlayer) {
        byPlayer.remove(player.uuid)
    }

    fun forget(playerId: UUID) {
        byPlayer.remove(playerId)
        lastSent.remove(playerId)
        history.remove(playerId)
    }

    fun reset() {
        byPlayer.clear()
        lastSent.clear()
        history.clear()
    }

    fun lastSent(playerId: UUID): AreaBoundsPayload? = lastSent[playerId]

    fun historyOf(playerId: UUID): List<AreaBoundsPayload> = history[playerId]?.toList().orEmpty()

    private fun watchersOf(level: ServerLevel, key: PastureKey): List<ServerPlayer> {
        val matched = ArrayList<ServerPlayer>()
        val iter = byPlayer.entries.iterator()
        while (iter.hasNext()) {
            val (_, watches) = iter.next()
            val player = watches[key] ?: continue
            if (player.isRemoved || player.serverLevel() !== level) {
                watches.remove(key)
                if (watches.isEmpty()) iter.remove()
            } else {
                matched += player
            }
        }
        return matched
    }

    private fun removeWatch(playerId: UUID, key: PastureKey) {
        val watches = byPlayer[playerId] ?: return
        watches.remove(key)
        if (watches.isEmpty()) byPlayer.remove(playerId)
    }

    private fun removePasture(key: PastureKey) {
        val iter = byPlayer.entries.iterator()
        while (iter.hasNext()) {
            val watches = iter.next().value
            watches.remove(key)
            if (watches.isEmpty()) iter.remove()
        }
    }

    private fun send(player: ServerPlayer, payload: AreaBoundsPayload) {
        lastSent[player.uuid] = payload
        val queue = history.getOrPut(player.uuid) { ArrayDeque() }
        if (queue.size >= MAX_HISTORY) queue.removeFirst()
        queue.addLast(payload)
        PacketDistributor.sendToPlayer(player, payload)
    }
}
