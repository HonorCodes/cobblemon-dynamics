package codes.honor.cobblemondynamics.claim

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import java.util.UUID

/** What a claim holds. The dimension is part of a block key, so equal coordinates in two dimensions never collide. */
sealed interface TargetKey {
    data class Block(val dimension: ResourceLocation, val pos: Long) : TargetKey

    data class Entity(val uuid: UUID) : TargetKey

    companion object {
        /** [pos] is the handler's canonical position, so every block of a multi-block target maps to one key (CD-CLM-04). */
        fun block(dimension: ResourceLocation, pos: BlockPos) = Block(dimension, pos.asLong())
    }
}
