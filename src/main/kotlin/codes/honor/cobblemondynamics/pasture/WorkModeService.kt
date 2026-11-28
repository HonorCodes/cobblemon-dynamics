package codes.honor.cobblemondynamics.pasture

import codes.honor.cobblemondynamics.compat.TeamAllies
import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.engine.WorkEngine
import codes.honor.cobblemondynamics.net.PanelViewers
import codes.honor.cobblemondynamics.registry.DynamicsAttachments
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import java.util.UUID

/** Applies Work Mode and Access Lock requests on the server thread, in the order they arrive (CD-SEC-06, CD-TEAM-01 to CD-TEAM-03). */
object WorkModeService {
    private const val PLAYER_PERMISSION_OPERATOR = 2

    /** The tick of each player's last accepted request (CD-SEC-03). In memory only. */
    private val lastAccepted = HashMap<UUID, Long>()

    /**
     * [level] is the pasture's level, which is the player's own unless the caller names another. The requested state is
     * applied as given, never flipped, so a repeated request gives the same result (CD-SEC-05).
     */
    fun request(
        player: ServerPlayer,
        pasturePos: BlockPos,
        enabled: Boolean,
        level: ServerLevel = player.serverLevel(),
    ): WorkModePolicy.Decision {
        val be = PastureAdapter.pastureAt(level, pasturePos) ?: return WorkModePolicy.Decision.Reject("no_pasture")
        val now = level.gameTime
        val decision = WorkModePolicy.decide(
            requester = player.uuid,
            isOperator = player.hasPermissions(PLAYER_PERMISSION_OPERATOR),
            placer = PastureAdapter.placer(be),
            distance = player.position().distanceTo(Vec3.atCenterOf(be.blockPos)),
            sameDimension = player.level() === level,
            now = now,
            lastAccepted = lastAccepted[player.uuid],
            cooldownTicks = WorkEngineSettings.current.toggleCooldownTicks,
            locked = PastureAdapter.isLocked(be),
            tetheredOwners = PastureAdapter.contributorIds(be),
        ) { a, b -> TeamAllies.areTeammates(level.server, a, b) }
        if (decision == WorkModePolicy.Decision.Apply) {
            lastAccepted[player.uuid] = now
            apply(level, be.blockPos, enabled)
        }
        return decision
    }

    /** Applies an Access Lock request when [player] is authorized under CD-TEAM-03. */
    fun requestLock(
        player: ServerPlayer,
        pasturePos: BlockPos,
        locked: Boolean,
        level: ServerLevel = player.serverLevel(),
    ): WorkModePolicy.Decision {
        val be = PastureAdapter.pastureAt(level, pasturePos) ?: return WorkModePolicy.Decision.Reject("no_pasture")
        val now = level.gameTime
        val decision = WorkModePolicy.decideLock(
            requester = player.uuid,
            isOperator = player.hasPermissions(PLAYER_PERMISSION_OPERATOR),
            placer = PastureAdapter.placer(be),
            distance = player.position().distanceTo(Vec3.atCenterOf(be.blockPos)),
            sameDimension = player.level() === level,
            now = now,
            lastAccepted = lastAccepted[player.uuid],
            cooldownTicks = WorkEngineSettings.current.toggleCooldownTicks,
        ) { a, b -> TeamAllies.areTeammates(level.server, a, b) }
        if (decision == WorkModePolicy.Decision.Apply) {
            lastAccepted[player.uuid] = now
            applyLock(level, be.blockPos, locked)
        }
        return decision
    }

    /** Sets Work Mode with no checks while keeping the pasture's [WorkModeData.locked] flag. [request] and the tests use it. */
    fun apply(level: ServerLevel, pasturePos: BlockPos, enabled: Boolean) {
        val be = PastureAdapter.pastureAt(level, pasturePos) ?: return
        val current = PastureAdapter.workModeData(be)
        be.setData(DynamicsAttachments.WORK_MODE, current.copy(enabled = enabled))
        be.setChanged()
        val index = level.getData(DynamicsAttachments.WORK_PASTURES)
        if (enabled) index.add(pasturePos) else index.remove(pasturePos)
        level.setData(DynamicsAttachments.WORK_PASTURES, index)
        WorkEngine.get()?.onWorkModeChanged(level, pasturePos, enabled)
        PanelViewers.broadcast(level, pasturePos)
    }

    /** Sets Access Lock with no checks while keeping [WorkModeData.enabled]. [requestLock] and the tests use it. */
    fun applyLock(level: ServerLevel, pasturePos: BlockPos, locked: Boolean) {
        val be = PastureAdapter.pastureAt(level, pasturePos) ?: return
        val current = PastureAdapter.workModeData(be)
        be.setData(DynamicsAttachments.WORK_MODE, current.copy(locked = locked))
        be.setChanged()
        PanelViewers.broadcast(level, pasturePos)
    }

    /** Forgets the last request of a player who logged out, so the map holds only players on the server. */
    fun forget(playerId: UUID) {
        lastAccepted.remove(playerId)
    }

    /** Forgets every player's last request when a server stops, because the next server runs on another clock. */
    fun reset() {
        lastAccepted.clear()
    }
}
