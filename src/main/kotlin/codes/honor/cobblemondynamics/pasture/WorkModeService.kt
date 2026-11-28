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

    fun requestSettings(
        player: ServerPlayer,
        pasturePos: BlockPos,
        radius: Int?,
        locked: Boolean?,
        level: ServerLevel = player.serverLevel(),
    ): WorkModePolicy.Decision {
        if (radius == null && locked == null) return WorkModePolicy.Decision.Reject("empty")
        val be = PastureAdapter.pastureAt(level, pasturePos) ?: return WorkModePolicy.Decision.Reject("no_pasture")
        val now = level.gameTime
        val settings = WorkEngineSettings.current
        val isOperator = player.hasPermissions(PLAYER_PERMISSION_OPERATOR)
        val placer = PastureAdapter.placer(be)
        val distance = player.position().distanceTo(Vec3.atCenterOf(be.blockPos))
        val sameDimension = player.level() === level
        val prevAccepted = lastAccepted[player.uuid]
        if (locked != null) {
            val lockDecision = WorkModePolicy.decideLock(
                requester = player.uuid,
                isOperator = isOperator,
                placer = placer,
                distance = distance,
                sameDimension = sameDimension,
                now = now,
                lastAccepted = prevAccepted,
                cooldownTicks = settings.toggleCooldownTicks,
            ) { a, b -> TeamAllies.areTeammates(level.server, a, b) }
            if (lockDecision != WorkModePolicy.Decision.Apply) return lockDecision
        }
        if (radius != null) {
            val maxHorizontal = minOf(settings.maxHorizontalRadius, PastureAdapter.maxWanderDistance())
            val radiusDecision = WorkModePolicy.decideRadius(
                requester = player.uuid,
                isOperator = isOperator,
                placer = placer,
                radius = radius,
                maxHorizontalRadius = maxHorizontal,
                distance = distance,
                sameDimension = sameDimension,
                now = now,
                lastAccepted = prevAccepted,
                cooldownTicks = settings.toggleCooldownTicks,
                locked = locked ?: PastureAdapter.isLocked(be),
                tetheredOwners = PastureAdapter.contributorIds(be),
            ) { a, b -> TeamAllies.areTeammates(level.server, a, b) }
            if (radiusDecision != WorkModePolicy.Decision.Apply) return radiusDecision
        }
        lastAccepted[player.uuid] = now
        if (locked != null && radius != null) {
            val current = PastureAdapter.workModeData(be)
            be.setData(DynamicsAttachments.WORK_MODE, current.copy(locked = locked, radius = radius))
            be.setChanged()
            WorkEngine.get()?.onPastureRadiusChanged(level, be.blockPos)
            PanelViewers.broadcast(level, be.blockPos)
            AreaWatchers.broadcast(level, be.blockPos)
        } else if (locked != null) {
            applyLock(level, be.blockPos, locked)
        } else if (radius != null) {
            applyRadius(level, be.blockPos, radius)
        }
        return WorkModePolicy.Decision.Apply
    }

    fun requestRadius(
        player: ServerPlayer,
        pasturePos: BlockPos,
        radius: Int,
        level: ServerLevel = player.serverLevel(),
    ): WorkModePolicy.Decision = requestSettings(player, pasturePos, radius = radius, locked = null, level = level)

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
        AreaWatchers.broadcast(level, pasturePos)
    }

    /** Sets Access Lock with no checks while keeping [WorkModeData.enabled]. [requestLock] and the tests use it. */
    fun applyLock(level: ServerLevel, pasturePos: BlockPos, locked: Boolean) {
        val be = PastureAdapter.pastureAt(level, pasturePos) ?: return
        val current = PastureAdapter.workModeData(be)
        be.setData(DynamicsAttachments.WORK_MODE, current.copy(locked = locked))
        be.setChanged()
        PanelViewers.broadcast(level, pasturePos)
    }

    fun applyRadius(level: ServerLevel, pasturePos: BlockPos, radius: Int?) {
        val be = PastureAdapter.pastureAt(level, pasturePos) ?: return
        val current = PastureAdapter.workModeData(be)
        be.setData(DynamicsAttachments.WORK_MODE, current.copy(radius = radius))
        be.setChanged()
        WorkEngine.get()?.onPastureRadiusChanged(level, pasturePos)
        PanelViewers.broadcast(level, pasturePos)
        AreaWatchers.broadcast(level, pasturePos)
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
