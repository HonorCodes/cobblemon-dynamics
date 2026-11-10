package codes.honor.cobblemondynamics.claim

import net.minecraft.core.BlockPos
import net.minecraft.nbt.NbtOps
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

class LeashIndexTest {
    private val stoker = ResourceLocation.parse("cobblemon_dynamics:stoker")
    private val owner = UUID.randomUUID()
    private val pasture = BlockPos(7, 64, 7)

    @Test
    fun `CD-LSH-11 one target holds one entry, and a Pokemon's entries go with it`() {
        val index = LeashIndex(HashMap())
        val first = LeashIndex.Ref(UUID.randomUUID(), owner, pasture, stoker)
        val second = LeashIndex.Ref(UUID.randomUUID(), owner, pasture, stoker)
        index.put(BlockPos(1, 64, 1), first)
        index.put(BlockPos(2, 64, 2), second)
        assertEquals(first, index.get(BlockPos(1, 64, 1)))
        index.removePokemon(first.pokemonId)
        assertNull(index.get(BlockPos(1, 64, 1)))
        assertEquals(second, index.get(BlockPos(2, 64, 2)))
        index.remove(BlockPos(2, 64, 2))
        assertEquals(emptyMap<BlockPos, LeashIndex.Ref>(), index.all())
    }

    @Test
    fun `CD-CLM-06 reservations survive the codec that saves them with the level`() {
        val index = LeashIndex(HashMap())
        index.put(BlockPos(1, 64, 1), LeashIndex.Ref(UUID.randomUUID(), owner, pasture, stoker))
        index.put(BlockPos(-5, 3, 9), LeashIndex.Ref(UUID.randomUUID(), owner, BlockPos(-5, 3, 5), stoker))
        val tag = LeashIndex.CODEC.encodeStart(NbtOps.INSTANCE, index).getOrThrow()
        assertEquals(index.all(), LeashIndex.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow().all())
    }
}
