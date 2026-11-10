package codes.honor.cobblemondynamics.leash

import codes.honor.cobblemondynamics.MOD_ID
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.UUIDUtil
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.Registries
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.UUID

/** The Pokémon that a Work Leash has selected. [name] is its display name when it was selected. */
data class LeashSelection(val pokemonId: UUID, val name: String)

/** The Work Leash state, on the stack. Both components are saved and synced, so the client knows when a block click is the leash's. */
object LeashComponents {
    val REGISTER: DeferredRegister.DataComponents = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MOD_ID)

    private val SELECTION_CODEC: Codec<LeashSelection> = RecordCodecBuilder.create { i ->
        i.group(
            UUIDUtil.CODEC.fieldOf("pokemon").forGetter(LeashSelection::pokemonId),
            Codec.STRING.fieldOf("name").forGetter(LeashSelection::name),
        ).apply(i, ::LeashSelection)
    }

    private val SELECTION_STREAM_CODEC: StreamCodec<ByteBuf, LeashSelection> = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC,
        LeashSelection::pokemonId,
        ByteBufCodecs.STRING_UTF8,
        LeashSelection::name,
        ::LeashSelection,
    )

    private val PENDING_CODEC: Codec<PendingSource> = RecordCodecBuilder.create { i ->
        i.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(PendingSource::pos),
            Direction.CODEC.fieldOf("face").forGetter(PendingSource::face),
            Codec.LONG.fieldOf("clicked_at").forGetter(PendingSource::clickedAt),
        ).apply(i, ::PendingSource)
    }

    private val PENDING_STREAM_CODEC: StreamCodec<ByteBuf, PendingSource> = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        PendingSource::pos,
        Direction.STREAM_CODEC,
        PendingSource::face,
        ByteBufCodecs.VAR_LONG,
        PendingSource::clickedAt,
        ::PendingSource,
    )

    val SELECTION: DeferredHolder<DataComponentType<*>, DataComponentType<LeashSelection>> =
        REGISTER.registerComponentType("leash_selection") { it.persistent(SELECTION_CODEC).networkSynchronized(SELECTION_STREAM_CODEC) }

    val PENDING_SOURCE: DeferredHolder<DataComponentType<*>, DataComponentType<PendingSource>> =
        REGISTER.registerComponentType("pending_source") { it.persistent(PENDING_CODEC).networkSynchronized(PENDING_STREAM_CODEC) }
}
