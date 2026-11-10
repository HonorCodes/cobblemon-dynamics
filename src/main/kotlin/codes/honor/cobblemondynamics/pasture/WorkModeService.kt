package codes.honor.cobblemondynamics.pasture

import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.registry.DynamicsAttachments
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import java.util.UUID

/** Applies Work Mode requests on the server thread, in the order they arrive (CD-SEC-06). */
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
        )
        if (decision == WorkModePolicy.Decision.Apply) {
            lastAccepted[player.uuid] = now
            apply(level, be.blockPos, enabled)
        }
        return decision
    }

    /** Sets Work Mode with no checks. [request] and the tests use it. */
    fun apply(level: ServerLevel, pasturePos: BlockPos, enabled: Boolean) {
        val be = PastureAdapter.pastureAt(level, pasturePos) ?: return
        be.setData(DynamicsAttachments.WORK_MODE, WorkModeData(enabled))
        val index = level.getData(DynamicsAttachments.WORK_PASTURES)
        if (enabled) index.add(pasturePos) else index.remove(pasturePos)
        level.setData(DynamicsAttachments.WORK_PASTURES, index)
    }

    /** Forgets every player's last request when a server stops, because the next server runs on another clock. */
    fun reset() {
        lastAccepted.clear()
    }
}
