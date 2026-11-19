package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.claim.TargetKey
import codes.honor.cobblemondynamics.role.RoleDefinition
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
    private fun candidate(x: Int, roles: List<RoleDefinition> = listOf(stoker), demand: Boolean = true) =
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

    /**
     * R84: the scan asks a releasing role's demand nearest first and stops at the first target with demand, so an idle Freezer beside a
     * lake does not run the full freeze test on every cell of it. The choice is the one of the nearest target with demand.
     */
    @Test
    fun `CD-CRP-10 CD-FRZ-12 an idle worker asks for demand nearest first and stops at the first target with it`() {
        var asked = 0
        fun counted(x: Int, demand: Boolean) =
            CandidateView(TargetKey.block(dim, BlockPos(x, 64, 0)), Vec3(x + 0.5, 64.5, 0.5), listOf(stoker), needsDemand = true) {
                asked++
                demand
            }
        val candidates = listOf(counted(9, true), counted(2, false), counted(5, true), counted(3, false), counted(7, true))
        assertEquals(TargetKey.block(dim, BlockPos(5, 64, 0)), Assigner.pickForIdle(fire(), candidates) { true }!!.first.key)
        assertEquals(3, asked, "the demand of the cells at 2, 3 and 5, and no other")
        asked = 0
        val current = TargetKey.block(dim, BlockPos(2, 64, 0))
        assertEquals(
            TargetKey.block(dim, BlockPos(5, 64, 0)),
            Assigner.pickForStandbyMove(fire(), current, candidates) {
                true
            }!!.first.key,
        )
        assertEquals(2, asked, "a Standby move asks the cells at 3 and 5 only")
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

    @Test
    fun `CD-ASG-16 a worker that fits two roles of a target takes the one with demand`() {
        val brewer = role("brewer", setOf("poison"), handler = "cobblemon_dynamics:brewing_speed")
        val brewStoker = role("brew_stoker", setOf("fire"), priority = 10, handler = "cobblemon_dynamics:brewing_fuel")
        val salazzle = fire(types = linkedSetOf("poison", "fire"))
        fun stand(withDemand: Set<RoleDefinition>) = CandidateView(
            TargetKey.block(dim, BlockPos(2, 64, 0)),
            Vec3(2.5, 64.5, 0.5),
            listOf(brewStoker, brewer),
            demandOf = { it in withDemand },
        ) { withDemand.isNotEmpty() }
        assertEquals(brewer, Assigner.pickForIdle(salazzle, listOf(stand(setOf(brewer)))) { true }?.second)
        assertEquals(brewStoker, Assigner.pickForIdle(salazzle, listOf(stand(setOf(brewStoker)))) { true }?.second)
        assertEquals(brewStoker, Assigner.pickForIdle(salazzle, listOf(stand(emptySet()))) { true }?.second)
    }

    @Test
    fun `CD-ASG-22 an unbound Bug-Ground worker gets no role at a tree`() {
        val lumberjack = role("lumberjack", setOf("bug"), autoAssign = false, handler = "cobblemon_dynamics:lumberjack")
        val nincada = WorkerView(UUID.randomUUID(), Vec3.ZERO, linkedSetOf("bug", "ground"), false, false) { false }
        assertNull(Assigner.pickForIdle(nincada, listOf(candidate(3, listOf(lumberjack)))) { true })
    }

    /** CD-CRP-06: a crop is one candidate in each lane, with the role of that lane. */
    @Test
    fun `CD-ASG-23 an unbound Grass-Ground worker takes the nearer crop of either lane with the role of that lane`() {
        val cultivator = role("cultivator", setOf("ground"), handler = "cobblemon_dynamics:cultivator")
        val harvester = role("harvester", setOf("grass"), handler = "cobblemon_dynamics:harvester")
        val torterra = WorkerView(UUID.randomUUID(), Vec3(0.5, 64.0, 0.5), linkedSetOf("grass", "ground"), false, false) { false }
        fun crop(x: Int, grown: Boolean) = listOf(
            CandidateView(TargetKey.block(dim, BlockPos(x, 64, 0), "cultivator"), Vec3(x + 0.5, 64.5, 0.5), listOf(cultivator), true) {
                !grown
            },
            CandidateView(TargetKey.block(dim, BlockPos(x, 64, 0), "harvester"), Vec3(x + 0.5, 64.5, 0.5), listOf(harvester), true) {
                grown
            },
        )
        val growingNear = Assigner.pickForIdle(torterra, crop(5, grown = true) + crop(2, grown = false)) { true }
        assertEquals(TargetKey.block(dim, BlockPos(2, 64, 0), "cultivator") to cultivator, growingNear?.let { it.first.key to it.second })
        val grownNear = Assigner.pickForIdle(torterra, crop(5, grown = false) + crop(2, grown = true)) { true }
        assertEquals(TargetKey.block(dim, BlockPos(2, 64, 0), "harvester") to harvester, grownNear?.let { it.first.key to it.second })
    }

    @Test
    fun `CD-CRP-10 an idle worker of a releasing role takes only a crop with demand, the nearest first`() {
        val grower = role("cultivator", setOf("ground"), handler = "cobblemon_dynamics:cultivator")
        val ground = WorkerView(UUID.randomUUID(), Vec3(0.5, 64.0, 0.5), setOf("ground"), false, false) { false }
        fun crop(x: Int, demand: Boolean) =
            CandidateView(TargetKey.block(dim, BlockPos(x, 64, 0), "cultivator"), Vec3(x + 0.5, 64.5, 0.5), listOf(grower), true) { demand }
        val grown = crop(1, demand = false)
        val growing = crop(4, demand = true)
        assertEquals(growing.key, Assigner.pickForIdle(ground, listOf(grown, growing)) { true }?.first?.key)
        assertNull(Assigner.pickForIdle(ground, listOf(grown)) { true })
        // A role that waits in Standby still takes the nearest target, demand or not (CD-ASG-03).
        val idleFurnace = candidate(1, demand = false)
        assertEquals(idleFurnace.key, Assigner.pickForIdle(fire(), listOf(idleFurnace, candidate(4))) { true }?.first?.key)
    }
}
