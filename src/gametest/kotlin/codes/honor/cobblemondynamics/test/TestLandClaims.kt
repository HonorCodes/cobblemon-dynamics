package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.compat.LandClaimCheck
import codes.honor.cobblemondynamics.compat.LandClaims
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * A claim mod for the engine path (CD-QWK-26, CD-QWK-29, CD-QWK-30, CD-LND-01 to CD-LND-05, CD-LND-11). Both questions give one answer
 * for each position. Tests add absolute positions and remove them after the test.
 */
object TestLandClaims : LandClaimCheck {
    val refused: MutableSet<BlockPos> = HashSet()
    val throwing: MutableSet<BlockPos> = HashSet()

    /** Positions whose break question refuses while the use question allows (CD-HRV-16). */
    val refusedBreak: MutableSet<BlockPos> = HashSet()

    /** The UUIDs that the pasture question got, for each position (CD-LND-05). */
    val askedAbout: MutableMap<BlockPos, MutableSet<UUID?>> = HashMap()

    fun register() = LandClaims.register(TEST_MOD_ID, this)

    override fun mayUse(owner: ServerPlayer, level: ServerLevel, pos: BlockPos): Boolean = answer(pos)

    override fun ownerMayUse(ownerId: UUID?, level: ServerLevel, pos: BlockPos): Boolean {
        askedAbout.getOrPut(pos.immutable()) { HashSet() } += ownerId
        return answer(pos)
    }

    override fun mayBreak(owner: ServerPlayer, level: ServerLevel, pos: BlockPos): Boolean = answer(pos) && pos !in refusedBreak

    override fun ownerMayBreak(ownerId: UUID?, level: ServerLevel, pos: BlockPos): Boolean = answer(pos) && pos !in refusedBreak

    private fun answer(pos: BlockPos): Boolean {
        if (pos in throwing) throw IllegalStateException("the test claim check fails at $pos")
        return pos !in refused
    }
}
