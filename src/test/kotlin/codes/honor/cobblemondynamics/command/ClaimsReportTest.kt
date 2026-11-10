package codes.honor.cobblemondynamics.command

import codes.honor.cobblemondynamics.claim.Claim
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.claim.LeashIndex
import codes.honor.cobblemondynamics.claim.TargetKey
import codes.honor.cobblemondynamics.pasture.PastureKey
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class ClaimsReportTest {
    private val overworld = ResourceLocation.parse("minecraft:overworld")
    private val nether = ResourceLocation.parse("minecraft:the_nether")
    private val stoker = ResourceLocation.parse("cobblemon_dynamics:stoker")
    private val pasture = PastureKey(overworld, BlockPos(7, 64, 7).asLong())

    @Test
    fun `CD-UI-14 a block target reads dimension x y z`() {
        assertEquals("minecraft:the_nether 12 64 -3", ClaimsReport.blockText(TargetKey.block(nether, BlockPos(12, 64, -3))))
    }

    @Test
    fun `CD-UI-14 every claim and every binding gives one line, claims first by kind, then bindings`() {
        val heal = Claim(UUID.randomUUID(), TargetKey.Entity(UUID.randomUUID()), ClaimKind.HEAL, pasture, 5)
        val bed = Claim(UUID.randomUUID(), TargetKey.block(overworld, BlockPos(2, 64, 2)), ClaimKind.BED, pasture, 6)
        val farWork = Claim(UUID.randomUUID(), TargetKey.block(overworld, BlockPos(9, 64, 9)), ClaimKind.WORK, pasture, 7)
        val work = Claim(UUID.randomUUID(), TargetKey.block(overworld, BlockPos(1, 64, 1)), ClaimKind.WORK, pasture, 8)
        val ref = LeashIndex.Ref(UUID.randomUUID(), UUID.randomUUID(), BlockPos.ZERO, stoker)
        val bound = TargetKey.block(overworld, BlockPos(4, 64, 4)) to ref
        val expected = listOf(
            ClaimsReport.Line.ClaimLine(work),
            ClaimsReport.Line.ClaimLine(farWork),
            ClaimsReport.Line.ClaimLine(bed),
            ClaimsReport.Line.ClaimLine(heal),
            ClaimsReport.Line.BindingLine(bound.first, bound.second),
        )
        assertEquals(expected, ClaimsReport.lines(listOf(heal, farWork, bed, work), listOf(bound)))
    }
}
