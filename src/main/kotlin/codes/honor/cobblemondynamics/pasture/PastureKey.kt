package codes.honor.cobblemondynamics.pasture

import net.minecraft.resources.ResourceLocation

/** A pasture block by dimension and `BlockPos.asLong()`. */
data class PastureKey(val dimension: ResourceLocation, val pos: Long)
