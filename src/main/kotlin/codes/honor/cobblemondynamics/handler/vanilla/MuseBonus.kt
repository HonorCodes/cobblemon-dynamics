package codes.honor.cobblemondynamics.handler.vanilla

import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.claim.TargetKey
import codes.honor.cobblemondynamics.engine.WorkEngine
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.engine.guarded
import com.google.gson.JsonObject
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.neoforged.fml.ModList
import kotlin.math.ceil

/**
 * What a Muse adds to its enchanting table, which a mixin reads: bookshelves for the vanilla menu, or Quanta for the stats of Apothic
 * Enchanting, whose menu reads no bookshelf count (CD-MUS-07).
 */
object MuseBonus {
    /** Vanilla's offers stop growing at 15 bookshelves: `EnchantmentHelper.getEnchantmentCost` clamps its count there. */
    const val MAX_BOOKSHELVES = 15

    /** Apothic Enchanting clamps each stat of a table to 0 to 100 in `EnchantmentTableStats`. */
    const val MAX_QUANTA = 100

    private const val APOTHIC_ENCHANTING = "apothic_enchanting"
    private const val AT_LEVEL_100 = "bookshelves_at_level_100"
    private const val DEFAULT_AT_LEVEL_100 = 15.0
    private const val QUANTA_AT_LEVEL_100 = "quanta_at_level_100"
    private const val DEFAULT_QUANTA_AT_LEVEL_100 = 45.0

    /** Whether Apothic Enchanting is loaded, read once. Outside a game, as in unit tests, there is no mod list, so it reads false. */
    val withApothicEnchanting: Boolean by lazy { ModList.get()?.isLoaded(APOTHIC_ENCHANTING) == true }

    /** A Muse's bonus. One of the two is 0. */
    data class Bonus(val bookshelves: Int, val quanta: Float)

    private val NONE = Bonus(0, 0f)

    /** CD-MUS-03: ceil(`bookshelves_at_level_100` × L ÷ 100), so ceil(15 × L ÷ 100) with the default role file. */
    fun forLevel(level: Int, atLevel100: Double): Int {
        val raw = ceil(atLevel100 * level.coerceAtLeast(0) / 100.0)
        if (raw.isNaN() || raw <= 0.0) return 0
        return minOf(raw, MAX_BOOKSHELVES.toDouble()).toInt()
    }

    /** The formula's number from the Muse's role file. A file that leaves it out, or gives the wrong type, takes 15. */
    fun atLevel100(params: JsonObject): Double = number(params, AT_LEVEL_100) ?: DEFAULT_AT_LEVEL_100

    /** CD-MUS-07: ceil(`quanta_at_level_100` × L ÷ 100), at most 100, so ceil(45 × L ÷ 100) with the default role file. */
    fun quantaForLevel(level: Int, atLevel100: Double): Float {
        val raw = ceil(atLevel100 * level.coerceAtLeast(0) / 100.0)
        if (raw.isNaN() || raw <= 0.0) return 0f
        return minOf(raw, MAX_QUANTA.toDouble()).toFloat()
    }

    /** The Quanta formula's number from the Muse's role file. A file that leaves it out, or gives the wrong type, takes 45. */
    fun quantaAtLevel100(params: JsonObject): Double = number(params, QUANTA_AT_LEVEL_100) ?: DEFAULT_QUANTA_AT_LEVEL_100

    /** CD-MUS-07: Quanta and no bookshelves with Apothic Enchanting, and bookshelves and no Quanta without it. */
    fun bonus(level: Int, params: JsonObject, apothicEnchanting: Boolean): Bonus =
        if (apothicEnchanting) Bonus(0, quantaForLevel(level, quantaAtLevel100(params))) else Bonus(forLevel(level, atLevel100(params)), 0f)

    /**
     * CD-MUS-04: the real count plus the Muse's, at most 15. With no Muse, the count stays exactly as vanilla passes it, and a count
     * above 15 from another mod's shelves is never lowered.
     */
    @JvmStatic
    fun capped(real: Int, bonus: Int): Int = if (bonus <= 0 || real >= MAX_BOOKSHELVES) real else minOf(MAX_BOOKSHELVES, real + bonus)

    /** The bookshelves of the Muse that Works at [table], 0 when none does or with Apothic Enchanting. The menu mixin calls this. */
    @JvmStatic
    fun of(level: Level, table: BlockPos): Int = at(level, table).bookshelves

    /** The Quanta of the Muse that Works at [table], 0 when none does. The stats mixin of Apothic Enchanting calls this on both sides. */
    @JvmStatic
    fun quantaOf(level: LevelReader, table: BlockPos): Float = at(level, table).quanta

    /** The engine lives on the server thread, so a client level or another thread gets no bonus. */
    private fun at(level: LevelReader, table: BlockPos): Bonus = guarded(EnchantingHandler, NONE) {
        val server = level as? ServerLevel ?: return@guarded NONE
        if (!server.server.isSameThread) return@guarded NONE
        val engine = WorkEngine.get() ?: return@guarded NONE
        val claim = engine.claims.holderOf(TargetKey.block(server.dimension().location(), table))
        if (claim?.kind != ClaimKind.WORK || engine.worker(claim.worker)?.status != WorkerStatus.WORKING) return@guarded NONE
        val role = engine.roleOf(claim.worker)?.takeIf { it.handler === EnchantingHandler } ?: return@guarded NONE
        val pokemon = engine.pokemonOf(claim.worker) ?: return@guarded NONE
        bonus(pokemon.level, role.definition.params, withApothicEnchanting)
    }

    private fun number(params: JsonObject, key: String): Double? =
        params.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble
}
