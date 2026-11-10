package codes.honor.cobblemondynamics.pasture

import com.mojang.serialization.Codec
import net.minecraft.core.BlockPos

/** The pastures of one level that are in Work Mode, by `BlockPos.asLong()`. Saved with the level. */
class WorkPastureIndex(private val positions: MutableSet<Long>) {
    fun add(pos: BlockPos) {
        positions.add(pos.asLong())
    }

    fun remove(pos: BlockPos) {
        positions.remove(pos.asLong())
    }

    /** A copy, so callers may change the index while they walk it. */
    fun all(): Set<Long> = positions.toSet()

    companion object {
        val CODEC: Codec<WorkPastureIndex> = Codec.LONG.listOf().xmap({ WorkPastureIndex(it.toHashSet()) }, { it.all().toList() })
    }
}
