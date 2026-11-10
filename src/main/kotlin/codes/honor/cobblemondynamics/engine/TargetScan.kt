package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.role.ActiveRole
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/** The world as a scan pass sees it. Unit tests use a fixed one. */
interface ScanWorld {
    /** Only block entities of loaded chunks. */
    fun blockEntitiesIn(area: WorkArea): Sequence<Pair<BlockPos, BlockEntity>>

    fun blockState(x: Int, y: Int, z: Int): BlockState

    /** The canonical position of the target that [handler] finds at [pos], or null when it finds none there. */
    fun match(handler: TargetHandler, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): BlockPos?
}

data class FoundTarget(val pos: BlockPos, val roles: List<ActiveRole>)

/**
 * One scan pass of a pasture's work area. Block entities are read in the first tick. Block positions are read only when
 * [workersNeedBlockScan], at most [budget] per tick (CD-ASG-07, CD-PERF-03).
 */
class TargetScan(private val area: WorkArea, roles: List<ActiveRole>, workersNeedBlockScan: Boolean, budget: Int) {
    private val entityRoles = roles.filter { it.handler.scanKind == ScanKind.BLOCK_ENTITY }
    private val blockRoles = if (workersNeedBlockScan) roles.filter { it.handler.scanKind == ScanKind.BLOCK } else emptyList()
    private val cursor = if (blockRoles.isEmpty()) {
        null
    } else {
        BlockScanCursor(area.minX, area.minY, area.minZ, area.maxX, area.maxY, area.maxZ, budget)
    }
    private val targets = LinkedHashMap<BlockPos, MutableList<ActiveRole>>()

    /** In the order the pass found them. */
    val found: List<FoundTarget> get() = targets.map { (pos, roles) -> FoundTarget(pos, roles.toList()) }

    fun startPass(world: ScanWorld) {
        if (entityRoles.isEmpty()) return
        for ((pos, blockEntity) in world.blockEntitiesIn(area)) {
            for (role in entityRoles) world.match(role.handler, pos, blockEntity.blockState, blockEntity)?.let { add(it, role) }
        }
    }

    /** Returns true when the pass is complete. */
    fun stepBlocks(world: ScanWorld): Boolean {
        val c = cursor ?: return true
        if (!c.done) {
            val pos = BlockPos.MutableBlockPos()
            c.step { x, y, z ->
                val state = world.blockState(x, y, z)
                if (!state.isAir) {
                    pos.set(x, y, z)
                    for (role in blockRoles) world.match(role.handler, pos.immutable(), state, null)?.let { add(it, role) }
                }
            }
        }
        return c.done
    }

    private fun add(canonical: BlockPos, role: ActiveRole) {
        val roles = targets.getOrPut(canonical) { ArrayList(1) }
        if (role !in roles) roles += role
    }
}
