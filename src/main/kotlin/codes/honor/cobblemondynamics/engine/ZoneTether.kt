package codes.honor.cobblemondynamics.engine

import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

object ZoneTether {
    const val MARGIN = 2
    const val DEFAULT_FOLLOW_RANGE = 32.0
    const val MIN_STEP = 16.0
    const val MAX_STEP = 24.0
    const val DEFAULT_PATH_CHECKS = 4
    private const val DEFAULT_STEP = 20.0
    private const val MIN_PROGRESS = 8.0
    private const val DETOUR_MIN_PROGRESS = 2.0
    private val STEP_DISTANCES = doubleArrayOf(20.0, 22.0, 18.0, 24.0, 16.0)
    private val ANGLES_RAD = doubleArrayOf(
        0.0,
        Math.toRadians(-25.0),
        Math.toRadians(25.0),
        Math.toRadians(-50.0),
        Math.toRadians(50.0),
        Math.toRadians(-70.0),
        Math.toRadians(70.0),
    )
    private val DY_OFFSETS = intArrayOf(0, -1, 1, -2, 2)
    private val LATERAL_OFFSETS = intArrayOf(0, -1, 1, -2, 2, -3, 3)

    data class Waypoint(val pos: Vec3, val cell: BlockPos = BlockPos.containing(pos))

    fun isInsideArea(pos: Vec3, area: WorkArea): Boolean = pos.x >= area.minX && pos.x <= area.maxX + 1.0 &&
        pos.y >= area.minY && pos.y <= area.maxY + 1.0 &&
        pos.z >= area.minZ && pos.z <= area.maxZ + 1.0

    fun isInsideMargin(pos: Vec3, area: WorkArea, margin: Int = MARGIN): Boolean =
        pos.x >= area.minX - margin && pos.x <= area.maxX + 1.0 + margin &&
            pos.y >= area.minY - margin && pos.y <= area.maxY + 1.0 + margin &&
            pos.z >= area.minZ - margin && pos.z <= area.maxZ + 1.0 + margin

    fun returnGoal(from: Vec3, area: WorkArea): Vec3 {
        val inset = if (area.horizontal >= 2) 1.0 else 0.0
        val x = from.x.coerceIn(area.minX + inset + 0.5, area.maxX + 0.5 - inset)
        val z = from.z.coerceIn(area.minZ + inset + 0.5, area.maxZ + 0.5 - inset)
        val y = (area.center.y + 1.0).coerceIn(area.minY.toDouble(), area.maxY + 0.5)
        return Vec3(x, y, z)
    }

    fun shouldSuppressIdleWalk(workMode: Boolean, entityTarget: Boolean, targetPos: BlockPos?, area: WorkArea): Boolean {
        if (!workMode) return false
        return entityTarget || (targetPos != null && !area.contains(targetPos))
    }

    fun waypointCandidates(
        from: Vec3,
        goal: Vec3,
        followRange: Double = DEFAULT_FOLLOW_RANGE,
        standable: (Int, Int, Int) -> Double?,
        fitsBody: (BlockPos, Double) -> Boolean = { _, _ -> true },
    ): List<Waypoint> {
        val maxRange = if (followRange.isFinite() && followRange > 0.0) followRange else DEFAULT_FOLLOW_RANGE
        val dist = from.distanceTo(goal)
        if (dist <= maxRange) return listOf(Waypoint(goal, BlockPos.containing(goal)))
        val dxGoal = goal.x - from.x
        val dzGoal = goal.z - from.z
        val horiz = hypot(dxGoal, dzGoal)
        val ux = if (horiz > 1e-4) dxGoal / horiz else 1.0
        val uz = if (horiz > 1e-4) dzGoal / horiz else 0.0
        val uy = (goal.y - from.y) / dist
        val minStep = MIN_STEP.coerceAtMost(maxRange * 0.5)
        val maxStep = MAX_STEP.coerceAtMost(maxRange)
        val seen = HashSet<Long>()
        val results = ArrayList<Waypoint>(ANGLES_RAD.size)
        for (angle in ANGLES_RAD) {
            val cosA = cos(angle)
            val sinA = sin(angle)
            val rx = ux * cosA - uz * sinA
            val rz = ux * sinA + uz * cosA
            val minProgress = if (angle == 0.0) MIN_PROGRESS else DETOUR_MIN_PROGRESS
            var chosenForRay: Waypoint? = null
            for (step in STEP_DISTANCES) {
                if (step > maxStep || chosenForRay != null) continue
                val probeX = from.x + rx * step
                val probeY = from.y + uy * step
                val probeZ = from.z + rz * step
                val baseX = Mth.floor(probeX)
                val baseY = Mth.floor(probeY)
                val baseZ = Mth.floor(probeZ)
                for (dy in DY_OFFSETS) {
                    if (chosenForRay != null) break
                    for (dx in LATERAL_OFFSETS) {
                        if (chosenForRay != null) break
                        for (dz in LATERAL_OFFSETS) {
                            val cx = baseX + dx
                            val cy = baseY + dy
                            val cz = baseZ + dz
                            val packed = BlockPos.asLong(cx, cy, cz)
                            if (packed in seen) continue
                            val topY = standable(cx, cy, cz) ?: continue
                            val cell = BlockPos(cx, cy, cz)
                            if (!fitsBody(cell, topY)) continue
                            val candidate = Vec3(cx + 0.5, Arrival.snapY(topY), cz + 0.5)
                            val candDist = from.distanceTo(candidate)
                            if (candDist in minStep..maxStep && candidate.distanceTo(goal) < dist - minProgress) {
                                seen += packed
                                chosenForRay = Waypoint(candidate, cell)
                                break
                            }
                        }
                    }
                }
            }
            if (chosenForRay != null) results += chosenForRay
        }
        return results
    }

    fun selectWaypoint(
        from: Vec3,
        goal: Vec3,
        goalCell: BlockPos = BlockPos.containing(goal),
        followRange: Double = DEFAULT_FOLLOW_RANGE,
        attemptOffset: Int = 0,
        maxPathChecks: Int = DEFAULT_PATH_CHECKS,
        standable: (Int, Int, Int) -> Double?,
        fitsBody: (BlockPos, Double) -> Boolean = { _, _ -> true },
        reachable: (BlockPos) -> Boolean = { true },
    ): Waypoint? {
        val maxRange = if (followRange.isFinite() && followRange > 0.0) followRange else DEFAULT_FOLLOW_RANGE
        if (from.distanceTo(goal) <= maxRange) return Waypoint(goal, goalCell)
        val candidates = waypointCandidates(from, goal, maxRange, standable, fitsBody)
        if (candidates.isEmpty()) return null
        val shift = Math.floorMod(attemptOffset, candidates.size)
        val checks = maxPathChecks.coerceIn(1, candidates.size)
        for (i in 0 until checks) {
            val cand = candidates[(shift + i) % candidates.size]
            if (reachable(cand.cell)) return cand
        }
        return null
    }

    fun nextWaypoint(
        from: Vec3,
        goal: Vec3,
        followRange: Double = DEFAULT_FOLLOW_RANGE,
        standable: ((Int, Int, Int) -> Double?)? = null,
    ): Vec3 {
        val maxRange = if (followRange.isFinite() && followRange > 0.0) followRange else DEFAULT_FOLLOW_RANGE
        val dist = from.distanceTo(goal)
        if (dist <= maxRange) return goal
        if (standable != null) {
            selectWaypoint(from, goal, followRange = maxRange, standable = standable)?.let { return it.pos }
        }
        val dir = goal.subtract(from).scale(1.0 / dist)
        val minStep = MIN_STEP.coerceAtMost(maxRange * 0.5)
        val maxStep = MAX_STEP.coerceAtMost(maxRange)
        return from.add(dir.scale(DEFAULT_STEP.coerceIn(minStep, maxStep)))
    }
}
