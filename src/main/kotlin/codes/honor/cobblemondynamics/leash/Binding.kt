package codes.honor.cobblemondynamics.leash

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.UUIDUtil
import net.minecraft.resources.ResourceLocation
import java.util.Optional
import java.util.UUID

/**
 * A Work Leash binding: the player's exact assignment of one Pokémon to [target]. The dimension is the pasture's. A
 * source-and-destination role also holds the [destination] and its clicked face.
 */
data class Binding(
    val roleId: ResourceLocation,
    val dimension: ResourceLocation,
    val pasture: BlockPos,
    val target: BlockPos,
    val face: Direction,
    val binder: UUID,
    val destination: BlockPos? = null,
    val destinationFace: Direction? = null,
) {
    companion object {
        val CODEC: Codec<Binding> = RecordCodecBuilder.create { i ->
            i.group(
                ResourceLocation.CODEC.fieldOf("role").forGetter(Binding::roleId),
                ResourceLocation.CODEC.fieldOf("dimension").forGetter(Binding::dimension),
                BlockPos.CODEC.fieldOf("pasture").forGetter(Binding::pasture),
                BlockPos.CODEC.fieldOf("target").forGetter(Binding::target),
                Direction.CODEC.fieldOf("face").forGetter(Binding::face),
                UUIDUtil.CODEC.fieldOf("binder").forGetter(Binding::binder),
                BlockPos.CODEC.optionalFieldOf("destination").forGetter { Optional.ofNullable(it.destination) },
                Direction.CODEC.optionalFieldOf("destination_face").forGetter { Optional.ofNullable(it.destinationFace) },
            ).apply(i) { role, dimension, pasture, target, face, binder, destination, destinationFace ->
                Binding(role, dimension, pasture, target, face, binder, destination.orElse(null), destinationFace.orElse(null))
            }
        }
    }
}
