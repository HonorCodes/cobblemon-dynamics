package codes.honor.cobblemondynamics.stamina

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.config.EngineSettings
import codes.honor.cobblemondynamics.engine.WorkerStatus
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtIo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * The store on a plain `persistentData` compound, the same one that Cobblemon saves with the Pokémon. The GameTest
 * `cdSta04And05StaminaStartsFullAndFollowsThePokemon` runs it on Cobblemon's real PC, party and instance swap.
 */
class StaminaStoreTest {
    private val s = EngineSettings.DEFAULT

    /** What Cobblemon's save and load do to the compound: binary NBT out and in. */
    private fun saveAndLoad(data: CompoundTag): CompoundTag {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { NbtIo.write(data, it) }
        return DataInputStream(ByteArrayInputStream(bytes.toByteArray())).use { NbtIo.read(it) }
    }

    @Test
    fun `CD-STA-04 the value, the Tired flag and the game time survive a save`() {
        val data = CompoundTag()
        StaminaStore.write(data, Stamina(0.37, tired = true), savedAt = 12_345)
        val loaded = saveAndLoad(data)
        assertEquals(StaminaStore.Saved(0.37, true, 12_345), StaminaStore.read(loaded))
        val restored = StaminaStore.restore(loaded, now = 12_345, level = 10, rule = RecoveryRule.WORKER, s = s)
        assertEquals(37.0, restored.percent, 1e-9)
        assertTrue(restored.tired)
    }

    @Test
    fun `CD-STA-04 the entry sits beside the binding in the mod's compound`() {
        val data = CompoundTag()
        val root = CompoundTag()
        root.put("binding", CompoundTag().apply { putString("role", "cobblemon_dynamics:stoker") })
        data.put(MOD_ID, root)
        StaminaStore.write(data, Stamina(0.5), savedAt = 0)
        val mod = data.getCompound(MOD_ID)
        assertTrue(mod.contains("binding"), "the write dropped the binding")
        val entry = mod.getCompound("stamina")
        assertEquals(0.5, entry.getDouble("percent"), 0.0)
        assertFalse(entry.getBoolean("tired"))
        assertEquals(0L, entry.getLong("last_game_time"))
    }

    @Test
    fun `CD-STA-05 a Pokemon with no Dynamics data starts at 100 percent`() {
        val fresh = StaminaStore.restore(CompoundTag(), now = 50_000, level = 30, rule = RecoveryRule.WORKER, s = s)
        assertEquals(100.0, fresh.percent, 0.0)
        assertFalse(fresh.tired)
        val bindingOnly = CompoundTag().apply { put(MOD_ID, CompoundTag().apply { put("binding", CompoundTag()) }) }
        assertNull(StaminaStore.read(bindingOnly))
        assertEquals(100.0, StaminaStore.restore(bindingOnly, 50_000, 30, RecoveryRule.WORKER, s).percent, 0.0)
    }

    @Test
    fun `CD-STA-05 a damaged entry counts as none`() {
        val noValue = CompoundTag().apply { putBoolean("tired", true) }
        val data = CompoundTag().apply { put(MOD_ID, CompoundTag().apply { put("stamina", noValue) }) }
        assertNull(StaminaStore.read(data))
        val nan = CompoundTag()
        StaminaStore.write(nan, Stamina(Double.NaN), savedAt = 0)
        assertNull(StaminaStore.read(nan))
    }

    @Test
    fun `CD-STA-11 a Lv10 worker saved at 0 percent reads 50 percent 18,000 ticks later`() {
        val data = CompoundTag()
        StaminaStore.write(data, Stamina(0.0, tired = true), savedAt = 100_000)
        val restored = StaminaStore.restore(saveAndLoad(data), now = 118_000, level = 10, rule = RecoveryRule.WORKER, s = s)
        assertEquals(50.0, restored.percent, 0.1)
        assertTrue(restored.tired)
    }

    /** Elapsed time is game time, the clock that `/time set` and `/time add` never move (they set the day time only). */
    @Test
    fun `CD-STA-12 only game time moves the value`() {
        val data = CompoundTag()
        StaminaStore.write(data, Stamina(0.37), savedAt = 40_000)
        assertEquals(37.0, StaminaStore.restore(data, now = 40_000, level = 10, rule = RecoveryRule.WORKER, s = s).percent, 1e-9)
        assertEquals(37.0, StaminaStore.restore(data, now = 30_000, level = 10, rule = RecoveryRule.WORKER, s = s).percent, 1e-9)
    }

    @Test
    fun `ADR-0008 a flush on each state change and every flushIntervalTicks`() {
        assertTrue(FlushRule.isDue(null, WorkerStatus.IDLE, flushedAt = 0, now = 0, s = s), "a new worker was never flushed")
        assertTrue(FlushRule.isDue(WorkerStatus.TRAVELING, WorkerStatus.WORKING, flushedAt = 100, now = 101, s = s))
        assertFalse(FlushRule.isDue(WorkerStatus.WORKING, WorkerStatus.WORKING, flushedAt = 100, now = 699, s = s))
        assertTrue(FlushRule.isDue(WorkerStatus.WORKING, WorkerStatus.WORKING, flushedAt = 100, now = 700, s = s))
        val slow = s.copy(flushIntervalTicks = 1_000)
        assertFalse(FlushRule.isDue(WorkerStatus.WORKING, WorkerStatus.WORKING, flushedAt = 100, now = 1_099, s = slow))
        assertTrue(FlushRule.isDue(WorkerStatus.WORKING, WorkerStatus.WORKING, flushedAt = 100, now = 1_100, s = slow))
    }
}
