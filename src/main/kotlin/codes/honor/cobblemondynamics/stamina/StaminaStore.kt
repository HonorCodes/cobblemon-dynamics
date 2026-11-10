package codes.honor.cobblemondynamics.stamina

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.config.EngineSettings
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.leash.BindingStore
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag

/**
 * Work Stamina in `Pokemon.persistentData` under `cobblemon_dynamics` → `stamina` (ADR-0008), so it follows the Pokémon through
 * the PC, the party and restarts (CD-STA-04). Write to the PC's own instance (`Tethering.getPokemon()`): a pastured entity can
 * hold a stale one until Cobblemon swaps it, and `onChange` marks the PC for saving.
 *
 * Times are game time, which is the overworld's in every dimension and which `/time set` and `/time add` never move: they set
 * the day time only (CD-STA-12).
 */
object StaminaStore {
    private const val KEY = "stamina"

    /** ADR-0008 calls it percent. It holds 0.0 to 1.0. */
    private const val FRACTION = "percent"
    private const val TIRED = "tired"
    private const val LAST_GAME_TIME = "last_game_time"

    /** A saved value. [lastGameTime] is the first tick that the value does not include yet. */
    data class Saved(val fraction: Double, val tired: Boolean, val lastGameTime: Long)

    /** The saved value plus the recovery of the time since the save, or 100% for a Pokémon with no entry (CD-STA-05, CD-STA-11). */
    fun load(pokemon: Pokemon, now: Long, s: EngineSettings): Stamina =
        restore(pokemon.persistentData, now, pokemon.level, ruleOf(pokemon), s)

    /** [savedAt] is the first tick that [stamina] does not include yet. */
    fun save(pokemon: Pokemon, stamina: Stamina, savedAt: Long) {
        write(pokemon.persistentData, stamina, savedAt)
        pokemon.onChange()
    }

    /** The rule from the Pokémon's own data: its types and whether it holds a binding (CD-HEAL-13). */
    fun ruleOf(pokemon: Pokemon): RecoveryRule =
        RecoveryRule.of(pokemon.types.any { it == ElementalTypes.FAIRY }, BindingStore.read(pokemon) != null)

    fun restore(data: CompoundTag, now: Long, level: Int, rule: RecoveryRule, s: EngineSettings): Stamina {
        val saved = read(data) ?: return Stamina()
        val stamina = Stamina(saved.fraction, saved.tired)
        OfflineRecovery.apply(stamina, now - saved.lastGameTime, level, rule, s)
        return stamina
    }

    /** The entry, or null when there is none or it is damaged, which a load then treats as a new Pokémon. */
    fun read(data: CompoundTag): Saved? {
        if (!data.contains(MOD_ID, Tag.TAG_COMPOUND.toInt())) return null
        val root = data.getCompound(MOD_ID)
        if (!root.contains(KEY, Tag.TAG_COMPOUND.toInt())) return null
        val entry = root.getCompound(KEY)
        val numeric = Tag.TAG_ANY_NUMERIC.toInt()
        if (!entry.contains(FRACTION, numeric) || !entry.contains(LAST_GAME_TIME, numeric)) return null
        val fraction = entry.getDouble(FRACTION)
        if (fraction.isNaN()) return null
        return Saved(fraction.coerceIn(0.0, 1.0), entry.getBoolean(TIRED), entry.getLong(LAST_GAME_TIME))
    }

    fun write(data: CompoundTag, stamina: Stamina, savedAt: Long) {
        val entry = CompoundTag()
        entry.putDouble(FRACTION, stamina.fraction)
        entry.putBoolean(TIRED, stamina.tired)
        entry.putLong(LAST_GAME_TIME, savedAt)
        val root = data.getCompound(MOD_ID)
        root.put(KEY, entry)
        data.put(MOD_ID, root)
    }
}

/** Recovery for the time that a Pokémon spent away from the engine: in the PC, in the party or in an unloaded chunk (CD-STA-11). */
object OfflineRecovery {
    /** Always at the base rate: a bed's factor never applies to time away. Negative time, from a clock that ran further elsewhere, adds nothing. */
    fun apply(stamina: Stamina, elapsed: Long, level: Int, rule: RecoveryRule, s: EngineSettings) {
        if (elapsed > 0) stamina.rest(elapsed, level, rule, bedTier = null, s)
    }
}

/** When the engine writes a worker's live Work Stamina to its saved data, besides entity unload and server stop (ADR-0008). */
object FlushRule {
    fun isDue(flushedStatus: WorkerStatus?, status: WorkerStatus, flushedAt: Long, now: Long, s: EngineSettings): Boolean =
        flushedStatus != status || now - flushedAt >= s.flushIntervalTicks
}
