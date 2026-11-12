package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.compat.LandClaims
import net.minecraft.core.BlockPos

/** A claim mod for the engine path (CD-QWK-26, CD-QWK-29, CD-QWK-30). Tests add absolute positions and remove them after the test. */
object TestLandClaims {
    val refused: MutableSet<BlockPos> = HashSet()
    val throwing: MutableSet<BlockPos> = HashSet()

    fun register() = LandClaims.register(TEST_MOD_ID) { _, _, pos ->
        if (pos in throwing) throw IllegalStateException("the test claim check fails at $pos")
        pos !in refused
    }
}
