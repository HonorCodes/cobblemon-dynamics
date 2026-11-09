package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.claim.TargetKey
import codes.honor.cobblemondynamics.role.role
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

class AssignerTest {
    private val dim = ResourceLocation.withDefaultNamespace("overworld")
    private val stoker = role("stoker", setOf("fire"))
    private val pump = role("pump", setOf("water"), autoAssign = false)
    private fun candidate(x: Int, roles: List<codes.honor.cobblemondynamics.role.RoleDefinition> = listOf(stoker), demand: Boolean = true) =
        CandidateView(TargetKey.block(dim, BlockPos(x, 64, 0)), Vec3(x + 0.5, 64.5, 0.5), roles) { demand }
    private fun fire(tired: Boolean = false, fairy: Boolean = false, types: Set<String> = setOf("fire")) =
        WorkerView(UUID.randomUUID(), Vec3(0.5, 64.0, 0.5), types, fairy, tired) { false }

    @Test
    fun `CD-ASG-03 the nearest free target wins`() {
        val near = candidate(3)
        val far = candidate(6)
        assertEquals(near.key, Assigner.pickForIdle(fire(), listOf(far, near)) { true }!!.first.key)
        assertEquals(far.key, Assigner.pickForIdle(fire(), listOf(far, near)) { it != near.key }!!.first.key)
    }

    @Test
    fun `CD-ASG-04 a Fairy type never takes a machine through an auto claim`() {
        assertNull(Assigner.pickForIdle(fire(fairy = true, types = setOf("fire", "fairy")), listOf(candidate(3))) { true })
    }

    @Test
    fun `CD-ASG-05 a leash-only role is never auto-assigned`() {
        val water = WorkerView(UUID.randomUUID(), Vec3.ZERO, setOf("water"), false, false) { false }
        assertNull(Assigner.pickForIdle(water, listOf(candidate(3, listOf(pump)))) { true })
    }

    @Test
    fun `CD-STA-09 a Tired worker gets no machine`() {
        assertNull(Assigner.pickForIdle(fire(tired = true), listOf(candidate(3))) { true })
    }

    @Test
    fun `CD-ASG-19 a Standby move needs demand and never picks the current target`() {
        val current = candidate(3, demand = false)
        val empty = candidate(4, demand = false)
        val busy = candidate(6, demand = true)
        assertEquals(busy.key, Assigner.pickForStandbyMove(fire(), current.key, listOf(current, empty, busy)) { true }!!.first.key)
    }
}
