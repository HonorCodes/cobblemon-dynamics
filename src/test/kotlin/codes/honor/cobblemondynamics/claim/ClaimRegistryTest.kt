package codes.honor.cobblemondynamics.claim

import codes.honor.cobblemondynamics.pasture.PastureKey
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

class ClaimRegistryTest {
    private val overworld = ResourceLocation.withDefaultNamespace("overworld")
    private val nether = ResourceLocation.withDefaultNamespace("the_nether")
    private val pastureA = PastureKey(overworld, BlockPos(0, 64, 10).asLong())
    private val pastureB = PastureKey(overworld, BlockPos(40, 64, 10).asLong())
    private val furnace = TargetKey.block(overworld, BlockPos(0, 64, 0))
    private val reservations = mutableMapOf<TargetKey, UUID>()
    private val registry = ClaimRegistry { reservations[it] }
    private val a = UUID.randomUUID()
    private val b = UUID.randomUUID()

    @Test
    fun `CD-CLM-01 two pastures with different owners claim one furnace in the same tick`() {
        assertInstanceOf(ClaimResult.Granted::class.java, registry.tryClaim(a, furnace, ClaimKind.WORK, pastureA, 0))
        assertEquals(ClaimResult.TargetTaken, registry.tryClaim(b, furnace, ClaimKind.WORK, pastureB, 0))
        assertEquals(a, registry.holderOf(furnace)?.worker)
    }

    @Test
    fun `CD-CLM-02 a worker holds at most one claim of any kind`() {
        registry.tryClaim(a, furnace, ClaimKind.WORK, pastureA, 0)
        val second = TargetKey.block(overworld, BlockPos(3, 64, 0))
        assertEquals(ClaimResult.WorkerBusy, registry.tryClaim(a, second, ClaimKind.WORK, pastureA, 1))
        val bed = TargetKey.block(overworld, BlockPos(5, 64, 5))
        registry.tryClaim(b, bed, ClaimKind.BED, pastureA, 0)
        assertEquals(ClaimResult.WorkerBusy, registry.tryClaim(b, TargetKey.Entity(UUID.randomUUID()), ClaimKind.HEAL, pastureA, 1))
    }

    @Test
    fun `CD-CLM-03 equal coordinates in two dimensions do not collide`() {
        registry.tryClaim(a, TargetKey.block(overworld, BlockPos(0, 64, 0)), ClaimKind.WORK, pastureA, 0)
        val result = registry.tryClaim(b, TargetKey.block(nether, BlockPos(0, 64, 0)), ClaimKind.WORK, PastureKey(nether, 0), 0)
        assertInstanceOf(ClaimResult.Granted::class.java, result)
    }

    @Test
    fun `CD-CLM-06 a reserved target refuses every worker but the bound one`() {
        reservations[furnace] = a
        assertEquals(ClaimResult.Reserved, registry.tryClaim(b, furnace, ClaimKind.WORK, pastureB, 0))
        assertInstanceOf(ClaimResult.Granted::class.java, registry.tryClaim(a, furnace, ClaimKind.WORK, pastureA, 0))
    }

    @Test
    fun `CD-CLM-09 a binding is not a claim, so a Tired leashed worker gets a bed claim`() {
        reservations[furnace] = a
        val bed = TargetKey.block(overworld, BlockPos(5, 64, 5))
        assertInstanceOf(ClaimResult.Granted::class.java, registry.tryClaim(a, bed, ClaimKind.BED, pastureA, 0))
    }

    @Test
    fun `CD-HEAL-09 one heal claim per ally`() {
        val ally = TargetKey.Entity(UUID.randomUUID())
        registry.tryClaim(a, ally, ClaimKind.HEAL, pastureA, 0)
        assertEquals(ClaimResult.TargetTaken, registry.tryClaim(b, ally, ClaimKind.HEAL, pastureA, 0))
    }

    @Test
    fun `CD-WMD-05 releasing by pasture frees work, bed and heal claims`() {
        registry.tryClaim(a, furnace, ClaimKind.WORK, pastureA, 0)
        registry.tryClaim(b, TargetKey.Entity(UUID.randomUUID()), ClaimKind.HEAL, pastureA, 0)
        val other = UUID.randomUUID()
        registry.tryClaim(other, TargetKey.block(overworld, BlockPos(9, 64, 9)), ClaimKind.BED, pastureB, 0)
        assertEquals(2, registry.releaseAll { it.pasture == pastureA }.size)
        assertNull(registry.claimOf(a))
        assertNull(registry.claimOf(b))
        assertEquals(other, registry.all().single().worker)
    }

    @Test
    fun `CD-CLM-01 a Quick Work claim names no pasture and stays when a pasture releases its claims`() {
        assertInstanceOf(ClaimResult.Granted::class.java, registry.tryClaim(a, furnace, ClaimKind.WORK, null, 0))
        registry.tryClaim(b, TargetKey.block(overworld, BlockPos(3, 64, 0)), ClaimKind.WORK, pastureA, 0)
        registry.releaseAll { it.pasture == pastureA }
        assertEquals(a, registry.holderOf(furnace)?.worker)
        assertNull(registry.holderOf(furnace)?.pasture)
    }

    @Test
    fun `CD-CLM-06 CD-QWK-07 a leash binding refuses a Quick Work claim`() {
        reservations[furnace] = b
        assertEquals(ClaimResult.Reserved, registry.tryClaim(a, furnace, ClaimKind.WORK, null, 0))
    }

    @Test
    fun `release frees both indexes`() {
        registry.tryClaim(a, furnace, ClaimKind.WORK, pastureA, 0)
        registry.release(a)
        assertInstanceOf(ClaimResult.Granted::class.java, registry.tryClaim(b, furnace, ClaimKind.WORK, pastureB, 1))
    }

    private val chest = TargetKey.block(overworld, BlockPos(4, 64, 0))
    private val basin = TargetKey.block(overworld, BlockPos(8, 64, 0))
    private val sharing = ClaimRegistry({ CAP }) { reservations[it] }

    private fun share(worker: UUID, key: TargetKey, now: Long = 0): ClaimResult =
        sharing.tryClaim(worker, key, ClaimKind.WORK, pastureA, now, shared = true)

    private fun exclusive(worker: UUID, key: TargetKey, now: Long = 0): ClaimResult =
        sharing.tryClaim(worker, key, ClaimKind.WORK, pastureB, now)

    @Test
    fun `CD-CLM-10 five workers share one chest, and a sixth gets a claim only after one of the five leaves`() {
        val workers = List(CAP + 1) { UUID.randomUUID() }
        for ((i, worker) in workers.take(CAP).withIndex()) {
            assertInstanceOf(ClaimResult.Granted::class.java, share(worker, chest), "shared claim ${i + 1} of $CAP")
        }
        assertEquals(ClaimResult.Full, share(workers.last(), chest), "the sixth worker got a claim")
        assertEquals(workers.take(CAP).toSet(), sharing.sharedOn(chest).map { it.worker }.toSet())
        assertNull(sharing.holderOf(chest), "a shared claim became the exclusive claim of the chest")
        sharing.release(workers.first())
        assertInstanceOf(ClaimResult.Granted::class.java, share(workers.last(), chest, now = 1), "the free place stayed taken")
    }

    @Test
    fun `CD-CLM-10 CD-CFG-19 the cap is the configured value at each claim`() {
        var cap = 1
        val capped = ClaimRegistry({ cap }) { null }
        assertInstanceOf(ClaimResult.Granted::class.java, capped.tryClaim(a, chest, ClaimKind.WORK, pastureA, 0, shared = true))
        assertEquals(ClaimResult.Full, capped.tryClaim(b, chest, ClaimKind.WORK, pastureA, 0, shared = true))
        cap = 2
        assertInstanceOf(ClaimResult.Granted::class.java, capped.tryClaim(b, chest, ClaimKind.WORK, pastureA, 1, shared = true))
    }

    @Test
    fun `CD-CLM-11 an exclusive claim and shared claims hold one basin together, in either order`() {
        val pump = UUID.randomUUID()
        assertInstanceOf(ClaimResult.Granted::class.java, exclusive(pump, basin))
        assertInstanceOf(ClaimResult.Granted::class.java, share(a, basin), "the Pump's claim refused a shared claim")
        assertInstanceOf(ClaimResult.Granted::class.java, share(b, basin))
        assertEquals(ClaimResult.TargetTaken, exclusive(UUID.randomUUID(), basin), "a second exclusive claim was granted")
        assertEquals(pump, sharing.holderOf(basin)?.worker)
        sharing.release(pump)
        assertInstanceOf(ClaimResult.Granted::class.java, exclusive(pump, basin, now = 1), "the shared claims refused the Pump")
        assertEquals(setOf(a, b), sharing.sharedOn(basin).map { it.worker }.toSet())
    }

    @Test
    fun `CD-CLM-11 a binding of an exclusive role reserves the basin only against exclusive claims`() {
        val pump = UUID.randomUUID()
        reservations[basin] = pump
        assertInstanceOf(ClaimResult.Granted::class.java, share(a, basin), "the Pump's reservation refused a shared claim")
        assertEquals(ClaimResult.Reserved, exclusive(b, basin))
        assertInstanceOf(ClaimResult.Granted::class.java, exclusive(pump, basin))
    }

    @Test
    fun `CD-CLM-02 a worker with a shared claim asks for a second claim`() {
        share(a, chest)
        assertEquals(ClaimResult.WorkerBusy, share(a, basin))
        assertEquals(ClaimResult.WorkerBusy, exclusive(a, furnace))
    }

    @Test
    fun `CD-WMD-05 CD-CLM-08 shared claims are listed and released with every other claim`() {
        share(a, chest)
        sharing.tryClaim(b, chest, ClaimKind.WORK, pastureB, 0, shared = true)
        sharing.tryClaim(UUID.randomUUID(), furnace, ClaimKind.WORK, pastureA, 0)
        assertEquals(3, sharing.all().size)
        assertEquals(2, sharing.releaseAll { it.pasture == pastureA }.size)
        assertEquals(listOf(b), sharing.sharedOn(chest).map { it.worker })
        assertNull(sharing.claimOf(a))
    }

    private companion object {
        const val CAP = 5
    }
}
