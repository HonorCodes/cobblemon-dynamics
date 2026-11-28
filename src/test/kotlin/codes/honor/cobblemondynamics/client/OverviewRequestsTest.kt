package codes.honor.cobblemondynamics.client

import codes.honor.cobblemondynamics.claim.LeashIndex
import codes.honor.cobblemondynamics.pasture.PastureAccessPolicy
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class OverviewRequestsTest {
    /** The ticks of [held] (one entry per client tick) on which the client asks the server for its leash overview. */
    private fun requests(held: List<Boolean>): List<Int> {
        val requests = OverviewRequests()
        return held.indices.filter { requests.tick(held[it]) }
    }

    @Test
    fun `CD-UI-09 the client asks as the leash comes into the main hand, then every 20 ticks while it stays there`() {
        assertEquals(listOf(2, 22, 42), requests(List(2) { false } + List(45) { true }))
    }

    @Test
    fun `putting the leash away and taking it again asks at once`() {
        assertEquals(listOf(0, 7), requests(List(5) { true } + List(2) { false } + List(3) { true }))
    }

    @Test
    fun `without the leash the client never asks`() {
        assertEquals(emptyList<Int>(), requests(List(60) { false }))
    }

    @Test
    fun `CD-TEAM-05 overview filtering includes own bindings and authorized shared-pasture bindings only`() {
        val stoker = ResourceLocation.parse("cobblemon_dynamics:stoker")
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val c = UUID.randomUUID()
        val sharedPasture = BlockPos(0, 64, 0)
        val unrelatedPasture = BlockPos(50, 64, 50)
        val target1 = BlockPos(2, 64, 0)
        val target2 = BlockPos(52, 64, 50)
        val index = LeashIndex()
        index.put(target1, LeashIndex.Ref(UUID.randomUUID(), a, sharedPasture, stoker))
        index.put(target2, LeashIndex.Ref(UUID.randomUUID(), c, unrelatedPasture, stoker))

        val bTargets = index.visibleTo(b) { pos ->
            pos == sharedPasture && PastureAccessPolicy.mayControl(b, false, a, locked = false, tetheredOwners = setOf(a, b))
        }.map { it.first }
        assertEquals(listOf(target1), bTargets)
    }
}
