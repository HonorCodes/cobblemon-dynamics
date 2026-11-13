package codes.honor.cobblemondynamics.claim

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import java.util.UUID

/** What a claim holds. The dimension is part of a block key, so equal coordinates in two dimensions never collide. */
sealed interface TargetKey {
    /**
     * [lane] is the claim lane of a farming role, so that one crop holds one claim of each farming role (CD-CRP-06). Every other role
     * claims in the null lane, which is the key of 1.0.
     */
    data class Block(val dimension: ResourceLocation, val pos: Long, val lane: String? = null) : TargetKey

    data class Entity(val uuid: UUID) : TargetKey

    companion object {
        /** [pos] is the handler's canonical position, so every block of a multi-block target maps to one key (CD-CLM-04). */
        fun block(dimension: ResourceLocation, pos: BlockPos, lane: String? = null) = Block(dimension, pos.asLong(), lane)
    }
}
