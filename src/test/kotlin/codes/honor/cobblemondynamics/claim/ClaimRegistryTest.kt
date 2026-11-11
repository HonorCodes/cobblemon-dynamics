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
}
