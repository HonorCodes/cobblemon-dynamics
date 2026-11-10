package codes.honor.cobblemondynamics.compat

import net.minecraft.nbt.CompoundTag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A fake `Pokemon.persistentData` stands in for the Pokémon, as Cobbleworkers 2.0.7 reads the flag from that tag. */
class CobbleworkersRestTest {
    private fun resting(data: CompoundTag) = data.getBoolean(CobbleworkersRest.RESTING)

    private fun ours(data: CompoundTag) = data.getCompound(CobbleworkersRest.ROOT).getBoolean(CobbleworkersRest.OWNED)

    @Test
    fun `CD-CMP-01 one poll of a Work Mode worker sets the rest flag, so it is set within 20 ticks`() {
        val data = CompoundTag()
        assertTrue(CobbleworkersRest.mark(data))
        assertTrue(resting(data))
        assertTrue(ours(data))
        assertFalse(CobbleworkersRest.mark(data), "a second poll changes nothing")
        assertTrue(resting(data))
    }

    @Test
    fun `CD-CMP-01 the flag uses Cobbleworkers' own key at the top of the tag`() {
        assertEquals("cobbleworkers.resting", CobbleworkersRest.RESTING)
        val data = CompoundTag()
        CobbleworkersRest.mark(data)
        assertTrue(data.contains("cobbleworkers.resting"))
        assertEquals(setOf("cobbleworkers.resting", "cobblemon_dynamics"), data.allKeys)
    }

    @Test
    fun `CD-CMP-02 a flag that Dynamics set is cleared when Work Mode turns off`() {
        val data = CompoundTag()
        data.getCompound(CobbleworkersRest.ROOT).let { root ->
            root.putString("binding", "kept")
            data.put(CobbleworkersRest.ROOT, root)
        }
        CobbleworkersRest.mark(data)
        assertTrue(CobbleworkersRest.unmark(data))
        assertFalse(data.contains(CobbleworkersRest.RESTING))
        assertFalse(ours(data))
        assertEquals("kept", data.getCompound(CobbleworkersRest.ROOT).getString("binding"), "the other Dynamics data stays")
        assertFalse(CobbleworkersRest.unmark(data), "a second unmark changes nothing")
    }

    @Test
    fun `CD-CMP-02 a flag that the player set survives Work Mode on and off`() {
        val data = CompoundTag()
        data.putBoolean(CobbleworkersRest.RESTING, true)
        assertFalse(CobbleworkersRest.mark(data), "Dynamics takes no ownership of the player's flag")
        assertFalse(ours(data))
        assertFalse(CobbleworkersRest.unmark(data))
        assertTrue(resting(data))
    }

    @Test
    fun `CD-CMP-02 a flag that the player cleared during Work Mode comes back at the next poll and goes when Work Mode ends`() {
        val data = CompoundTag()
        CobbleworkersRest.mark(data)
        data.remove(CobbleworkersRest.RESTING)
        assertTrue(CobbleworkersRest.mark(data))
        assertTrue(resting(data))
        assertTrue(CobbleworkersRest.unmark(data))
        assertFalse(data.contains(CobbleworkersRest.RESTING))
    }

    @Test
    fun `an unmark leaves no empty Dynamics compound behind`() {
        val data = CompoundTag()
        CobbleworkersRest.mark(data)
        CobbleworkersRest.unmark(data)
        assertTrue(data.isEmpty, "left $data")
    }
}
