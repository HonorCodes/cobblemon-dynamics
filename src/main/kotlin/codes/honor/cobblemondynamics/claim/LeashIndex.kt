package codes.honor.cobblemondynamics.claim

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.UUIDUtil
import net.minecraft.resources.ResourceLocation
import java.util.UUID

/**
 * The leash bindings of one level by target, so that the claim registry can read reservations (CD-CLM-06). A source-and-destination
 * binding is keyed by its source. Saved with the level, so a reservation holds after a restart, also while its pasture is off.
 */
class LeashIndex(private val entries: MutableMap<Long, Ref>) {
    data class Ref(val pokemonId: UUID, val owner: UUID, val pasture: BlockPos, val roleId: ResourceLocation)

    fun get(target: BlockPos): Ref? = entries[target.asLong()]

    fun put(target: BlockPos, ref: Ref) {
        entries[target.asLong()] = ref
    }

    fun remove(target: BlockPos) {
        entries.remove(target.asLong())
    }

    fun removePokemon(id: UUID) {
        entries.values.removeIf { it.pokemonId == id }
    }

    /** A copy, so callers may change the index while they walk it. */
    fun all(): Map<BlockPos, Ref> = entries.entries.associate { (pos, ref) -> BlockPos.of(pos) to ref }

    /** The entries of [owner]'s Pokémon, as a copy. It copies only those entries, where [all] copies the whole index. */
    fun allOwnedBy(owner: UUID): Map<BlockPos, Ref> =
        entries.entries.filter { it.value.owner == owner }.associate { (pos, ref) -> BlockPos.of(pos) to ref }

    companion object {
        private data class Entry(val target: Long, val ref: Ref)

        private val ENTRY: Codec<Entry> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.LONG.fieldOf("target").forGetter(Entry::target),
                UUIDUtil.CODEC.fieldOf("pokemon").forGetter { it.ref.pokemonId },
                UUIDUtil.CODEC.fieldOf("owner").forGetter { it.ref.owner },
                BlockPos.CODEC.fieldOf("pasture").forGetter { it.ref.pasture },
                ResourceLocation.CODEC.fieldOf("role").forGetter { it.ref.roleId },
            ).apply(i) { target, pokemon, owner, pasture, role -> Entry(target, Ref(pokemon, owner, pasture, role)) }
        }

        val CODEC: Codec<LeashIndex> = ENTRY.listOf().xmap(
            { list -> LeashIndex(list.associateTo(HashMap()) { it.target to it.ref }) },
            { index -> index.entries.map { (target, ref) -> Entry(target, ref) } },
        )
    }
}
