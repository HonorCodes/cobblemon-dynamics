package codes.honor.cobblemondynamics.engine

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WaypointStepTest {
    @Test
    fun `CD-NAV-07 returns final goal within 32 blocks and steps 16 to 24 blocks closer beyond 32`() {
        val start = Vec3(0.5, 2.0, 0.5)
        val exact32 = Vec3(32.5, 2.0, 0.5)
        assertEquals(exact32, ZoneTether.nextWaypoint(start, exact32, 32.0))

        val far45 = Vec3(45.5, 2.0, 0.5)
        val step = ZoneTether.nextWaypoint(start, far45, 32.0)
        val stepDist = start.distanceTo(step)
        val closer = start.distanceTo(far45) - step.distanceTo(far45)
        assertTrue(stepDist in 16.0..24.0, "stepDist $stepDist was not in 16..24")
        assertTrue(closer in 16.0..24.0, "closer $closer was not in 16..24")
        assertEquals(0.5, step.z, 1e-6)
    }

    @Test
    fun `CD-NAV-07 picks a standable intermediate column when a surface check is supplied`() {
        val start = Vec3(0.5, 2.0, 0.5)
        val far45 = Vec3(45.5, 2.0, 0.5)
        val blockedX = 20
        val step = ZoneTether.nextWaypoint(start, far45, 32.0) { x, y, _ ->
            if (x == blockedX) {
                null
            } else if (y == 2) {
                2.0
            } else {
                null
            }
        }
        assertTrue(step.x.toInt() != blockedX)
        assertTrue(start.distanceTo(step) in 16.0..24.0)
    }

    @Test
    fun `CD-NAV-07 selectWaypoint returns null when no candidate is reachable or standable and rotates slices`() {
        val start = Vec3(0.5, 2.0, 0.5)
        val far45 = Vec3(45.5, 2.0, 0.5)
        val openFloor: (Int, Int, Int) -> Double? = { _, y, _ -> if (y == 2) 2.0 else null }
        var pathChecks = 0
        val detour = ZoneTether.selectWaypoint(
            from = start,
            goal = far45,
            followRange = 32.0,
            standable = openFloor,
            fitsBody = { cell, _ -> cell.z != 0 },
            reachable = { cell ->
                pathChecks++
                cell.z <= -6
            },
        )
        assertNotNull(detour)
        assertTrue(detour!!.cell.z <= -6, "expected detour candidate with z <= -6, got ${detour.cell}")
        assertTrue(pathChecks <= 4, "expected at most 4 path checks, got $pathChecks")
        assertTrue(start.distanceTo(detour.pos) <= 32.0)
        assertTrue(detour.pos.distanceTo(far45) < start.distanceTo(far45))

        assertNull(
            ZoneTether.selectWaypoint(
                from = start,
                goal = far45,
                followRange = 32.0,
                standable = openFloor,
                reachable = { false },
            ),
        )
        assertNull(
            ZoneTether.selectWaypoint(
                from = start,
                goal = far45,
                followRange = 32.0,
                standable = { _, _, _ -> null },
                reachable = { true },
            ),
        )

        val allCandidates = ZoneTether.waypointCandidates(start, far45, 32.0, openFloor)
        assertTrue(allCandidates.size > ZoneTether.DEFAULT_PATH_CHECKS, "expected > 4 angular candidates, got ${allCandidates.size}")
        val fifthRayTarget = allCandidates[5].cell
        assertNull(
            ZoneTether.selectWaypoint(
                from = start,
                goal = far45,
                followRange = 32.0,
                attemptOffset = 0,
                maxPathChecks = 4,
                standable = openFloor,
                reachable = { it == fifthRayTarget },
            ),
        )
        val rotatedHit = ZoneTether.selectWaypoint(
            from = start,
            goal = far45,
            followRange = 32.0,
            attemptOffset = 4,
            maxPathChecks = 4,
            standable = openFloor,
            reachable = { it == fifthRayTarget },
        )
        assertNotNull(rotatedHit)
        assertEquals(fifthRayTarget, rotatedHit!!.cell)
    }
}
