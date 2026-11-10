package codes.honor.cobblemondynamics.command

import codes.honor.cobblemondynamics.claim.Claim
import codes.honor.cobblemondynamics.claim.LeashIndex
import codes.honor.cobblemondynamics.claim.TargetKey
import net.minecraft.core.BlockPos

/** The entries of `/cobblemondynamics claims`, before names and lang keys turn them into text (CD-UI-14). */
object ClaimsReport {
    sealed interface Line {
        data class ClaimLine(val claim: Claim) : Line

        data class BindingLine(val target: TargetKey.Block, val ref: LeashIndex.Ref) : Line
    }

    /** One line per claim and per binding: claims by kind, then target; then bindings by target. */
    fun lines(claims: Collection<Claim>, bindings: Collection<Pair<TargetKey.Block, LeashIndex.Ref>>): List<Line> =
        claims.sortedWith(compareBy<Claim> { it.kind }.thenBy(KEY_ORDER) { it.key }).map { Line.ClaimLine(it) } +
            bindings.sortedWith(compareBy(KEY_ORDER) { it.first }).map { (target, ref) -> Line.BindingLine(target, ref) }

    /** The target field of a block entry: `dimension x y z`. */
    fun blockText(key: TargetKey.Block): String {
        val pos = BlockPos.of(key.pos)
        return "${key.dimension} ${pos.x} ${pos.y} ${pos.z}"
    }

    /** Block keys by dimension, then `BlockPos.asLong()`, before entity keys by UUID. */
    private val KEY_ORDER: Comparator<TargetKey> = Comparator { a, b ->
        when {
            a is TargetKey.Block && b is TargetKey.Block -> compareValuesBy(a, b, { it.dimension.toString() }, { it.pos })
            a is TargetKey.Entity && b is TargetKey.Entity -> a.uuid.compareTo(b.uuid)
            a is TargetKey.Block -> -1
            else -> 1
        }
    }
}
