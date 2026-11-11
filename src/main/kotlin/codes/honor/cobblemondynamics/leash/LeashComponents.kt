package codes.honor.cobblemondynamics.leash

import codes.honor.cobblemondynamics.MOD_ID
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
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

    val SELECTION: DeferredHolder<DataComponentType<*>, DataComponentType<LeashSelection>> =
        REGISTER.registerComponentType("leash_selection") { it.persistent(SELECTION_CODEC).networkSynchronized(SELECTION_STREAM_CODEC) }

    val PENDING_SOURCE: DeferredHolder<DataComponentType<*>, DataComponentType<PendingSource>> =
        REGISTER.registerComponentType("pending_source") {
            it.persistent(PendingSource.CODEC).networkSynchronized(PendingSource.STREAM_CODEC)
        }
}
