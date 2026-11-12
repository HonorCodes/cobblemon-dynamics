package codes.honor.cobblemondynamics.claim

import net.minecraft.core.BlockPos
import net.minecraft.nbt.NbtOps
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class LeashIndexTest {
    private val stoker = ResourceLocation.parse("cobblemon_dynamics:stoker")
    private val magnet = ResourceLocation.parse("cobblemon_dynamics:magnet")
    private val telekinesis = ResourceLocation.parse("cobblemon_dynamics:telekinesis")
    private val shared: (ResourceLocation) -> Boolean = { it == magnet || it == telekinesis }
    private val owner = UUID.randomUUID()
    private val pasture = BlockPos(7, 64, 7)
    private val chestA = BlockPos(1, 64, 1)
    private val chestB = BlockPos(2, 64, 2)

    private fun ref(role: ResourceLocation, destination: BlockPos? = null, owner: UUID = this.owner) =
        LeashIndex.Ref(UUID.randomUUID(), owner, pasture, role, destination)

    @Test
    fun `CD-LSH-11 a Pokemon holds one entry, which a new bind moves and its removal ends`() {
        val index = LeashIndex()
        val first = ref(magnet)
        val second = ref(magnet)
        index.put(chestA, first)
        index.put(chestB, second)
        assertEquals(listOf(first), index.at(chestA))
        index.put(chestB, first)
        assertEquals(emptyList<LeashIndex.Ref>(), index.at(chestA), "the old entry stayed after the new bind")
        assertEquals(listOf(second, first), index.at(chestB))
        index.removePokemon(first.pokemonId)
        assertEquals(listOf(second), index.at(chestB))
        assertFalse(index.remove(chestB, first.pokemonId))
        assertTrue(index.remove(chestB, second.pokemonId))
        assertEquals(emptyList<Pair<BlockPos, LeashIndex.Ref>>(), index.all())
    }

    @Test
    fun `CD-LSH-28 CD-LSH-29 a container counts the places of its logistics bindings as target and as destination`() {
        val index = LeashIndex()
        val collector = ref(magnet)
        val mover = ref(telekinesis, destination = chestB)
        val feeder = ref(telekinesis, destination = chestA)
        index.put(chestA, collector)
        index.put(chestA, mover)
        index.put(BlockPos(5, 64, 5), feeder)
        assertEquals(listOf(collector, mover), index.at(chestA))
        assertEquals(3, index.placesAt(chestA, except = null, isShared = shared))
        assertEquals(2, index.placesAt(chestA, except = mover.pokemonId, isShared = shared), "a rebind counted its own old place")
        assertEquals(1, index.placesAt(chestB, except = null, isShared = shared))
    }

    @Test
    fun `CD-CLM-11 only the entry of an exclusive role reserves its target, and it takes no place`() {
        val index = LeashIndex()
        val furnace = BlockPos(3, 64, 3)
        val source = ref(telekinesis, destination = chestB)
        val fire = ref(stoker)
        index.put(furnace, source)
        assertNull(index.exclusiveAt(furnace, shared), "a Telekinesis source reserved the furnace")
        index.put(furnace, fire)
        assertEquals(fire, index.exclusiveAt(furnace, shared))
        assertEquals(1, index.placesAt(furnace, except = null, isShared = shared))
    }

    @Test
    fun `CD-UI-09 the entries of one owner are only that owner's`() {
        val index = LeashIndex()
        val own = ref(stoker)
        index.put(chestA, own)
        index.put(chestB, ref(stoker, owner = UUID.randomUUID()))
        assertEquals(listOf(chestA to own), index.allOwnedBy(owner))
        assertEquals(emptyList<Pair<BlockPos, LeashIndex.Ref>>(), index.allOwnedBy(UUID.randomUUID()))
    }

    @Test
    fun `CD-CLM-06 CD-LSH-29 reservations and destinations survive the codec that saves them with the level`() {
        val index = LeashIndex()
        index.put(chestA, ref(magnet))
        index.put(chestA, ref(telekinesis, destination = chestB))
        index.put(BlockPos(-5, 3, 9), LeashIndex.Ref(UUID.randomUUID(), owner, BlockPos(-5, 3, 5), stoker))
        val tag = LeashIndex.CODEC.encodeStart(NbtOps.INSTANCE, index).getOrThrow()
        val loaded = LeashIndex.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow()
        assertEquals(index.all().toSet(), loaded.all().toSet())
        assertEquals(index.at(chestA), loaded.at(chestA), "the codec changed the order of one target's entries")
    }
}
