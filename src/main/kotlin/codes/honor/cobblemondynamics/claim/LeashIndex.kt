package codes.honor.cobblemondynamics.claim

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.UUIDUtil
import net.minecraft.resources.ResourceLocation
import java.util.Optional
import java.util.UUID

/**
 * The leash bindings of one level by target, so that the claim registry can read reservations (CD-CLM-06) and a bind can count the
 * places of a container (CD-LSH-29). A source-and-destination binding is keyed by its source and names its destination. A target holds
 * one binding of an exclusive role, and any bindings of logistics roles (CD-LSH-28). Saved with the level, so a reservation holds after
 * a restart, also while its pasture is off.
 */
class LeashIndex {
    /** [destination] is the destination of a Telekinesis binding, where the binding takes a place too. */
    data class Ref(
        val pokemonId: UUID,
        val owner: UUID,
        val pasture: BlockPos,
        val roleId: ResourceLocation,
        val destination: BlockPos? = null,
    )

    private val byTarget = HashMap<Long, MutableList<Ref>>()

    /** The entries at [target], oldest first, as a copy. */
    fun at(target: BlockPos): List<Ref> = byTarget[target.asLong()]?.toList().orEmpty()

    /** The entry of an exclusive role at [target], which reserves it (CD-CLM-06). [isShared] tells the roles of logistics handlers. */
    fun exclusiveAt(target: BlockPos, isShared: (ResourceLocation) -> Boolean): Ref? =
        byTarget[target.asLong()]?.firstOrNull { !isShared(it.roleId) }

    /**
     * CD-LSH-29: the places that the bindings of logistics roles take at [container], as their target or as their destination. The
     * entry of [except] does not count, so a new binding of the same Pokémon keeps its own place.
     */
    fun placesAt(container: BlockPos, except: UUID?, isShared: (ResourceLocation) -> Boolean): Int {
        val key = container.asLong()
        return byTarget.entries.sumOf { (target, refs) ->
            refs.count { it.pokemonId != except && isShared(it.roleId) && (target == key || it.destination == container) }
        }
    }

    /** Adds [ref] at [target]. A Pokémon has one binding, so any other entry of that Pokémon leaves the index. */
    fun put(target: BlockPos, ref: Ref) {
        removePokemon(ref.pokemonId)
        add(target.asLong(), ref)
    }

    /** Removes the entry of [pokemonId] at [target]. Returns whether there was one. */
    fun remove(target: BlockPos, pokemonId: UUID): Boolean {
        val key = target.asLong()
        val refs = byTarget[key] ?: return false
        val removed = refs.removeIf { it.pokemonId == pokemonId }
        if (refs.isEmpty()) byTarget.remove(key)
        return removed
    }

    fun removePokemon(id: UUID) {
        val lists = byTarget.values.iterator()
        while (lists.hasNext()) {
            val refs = lists.next()
            refs.removeIf { it.pokemonId == id }
            if (refs.isEmpty()) lists.remove()
        }
    }

    /** A copy, so callers may change the index while they walk it. */
    fun all(): List<Pair<BlockPos, Ref>> = byTarget.flatMap { (target, refs) -> refs.map { BlockPos.of(target) to it } }

    /** The entries of [owner]'s Pokémon, as a copy. It copies only those entries, where [all] copies the whole index. */
    fun allOwnedBy(owner: UUID): List<Pair<BlockPos, Ref>> =
        byTarget.flatMap { (target, refs) -> refs.filter { it.owner == owner }.map { BlockPos.of(target) to it } }

    private fun add(target: Long, ref: Ref) {
        byTarget.getOrPut(target) { ArrayList() }.add(ref)
    }

    companion object {
        private data class Entry(val target: Long, val ref: Ref)

        private val ENTRY: Codec<Entry> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.LONG.fieldOf("target").forGetter(Entry::target),
                UUIDUtil.CODEC.fieldOf("pokemon").forGetter { it.ref.pokemonId },
                UUIDUtil.CODEC.fieldOf("owner").forGetter { it.ref.owner },
                BlockPos.CODEC.fieldOf("pasture").forGetter { it.ref.pasture },
                ResourceLocation.CODEC.fieldOf("role").forGetter { it.ref.roleId },
                BlockPos.CODEC.optionalFieldOf("destination").forGetter { Optional.ofNullable(it.ref.destination) },
            ).apply(i) { target, pokemon, owner, pasture, role, destination ->
                Entry(target, Ref(pokemon, owner, pasture, role, destination.orElse(null)))
            }
        }

        /** An index saved before entries named destinations loads with none. LeashResets fills them in from the bindings. */
        val CODEC: Codec<LeashIndex> = ENTRY.listOf().xmap(
            { list -> LeashIndex().apply { for (entry in list) add(entry.target, entry.ref) } },
            { index -> index.byTarget.flatMap { (target, refs) -> refs.map { Entry(target, it) } } },
        )
    }
}
