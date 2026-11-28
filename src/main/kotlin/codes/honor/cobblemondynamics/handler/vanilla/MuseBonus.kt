package codes.honor.cobblemondynamics.handler.vanilla

import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.claim.TargetKey
import codes.honor.cobblemondynamics.engine.WorkEngine
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.engine.guarded
import com.google.gson.JsonObject
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.enchantment.ItemEnchantments
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.neoforged.fml.ModList
import kotlin.math.ceil
import kotlin.math.roundToInt

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
    private const val INFUSER = "enchantinginfuser"
    private const val AT_LEVEL_100 = "bookshelves_at_level_100"
    private const val DEFAULT_AT_LEVEL_100 = 15.0
    private const val QUANTA_AT_LEVEL_100 = "quanta_at_level_100"
    private const val DEFAULT_QUANTA_AT_LEVEL_100 = 45.0
    private val CEI_INFUSER: ResourceLocation = ResourceLocation.fromNamespaceAndPath("create_enchantment_industry", "infuser")

    /** Whether Apothic Enchanting is loaded, read once. Outside a game, as in unit tests, there is no mod list, so it reads false. */
    val withApothicEnchanting: Boolean by lazy { ModList.get()?.isLoaded(APOTHIC_ENCHANTING) == true }

    /** A Muse's bonus. One of the two is 0. */
    data class Bonus(val bookshelves: Int, val quanta: Float)

    private val NONE = Bonus(0, 0f)

    /** CD-MUS-03: ceil(`bookshelves_at_level_100` × L ÷ 100), so ceil(15 × L ÷ 100) with the default role file. */
    fun forLevel(level: Int, atLevel100: Double, multiplier: Double = 1.0): Int {
        val base = ceil(atLevel100 * level.coerceAtLeast(0) / 100.0)
        if (base.isNaN() || base <= 0.0) return 0
        val scaled = if (multiplier == 1.0) base.toInt() else (base * multiplier).roundToInt()
        return scaled.coerceIn(0, MAX_BOOKSHELVES)
    }

    /** The formula's number from the Muse's role file. A file that leaves it out, or gives the wrong type, takes 15. */
    fun atLevel100(params: JsonObject): Double = number(params, AT_LEVEL_100) ?: DEFAULT_AT_LEVEL_100

    /** CD-MUS-07: ceil(`quanta_at_level_100` × L ÷ 100), at most 100, so ceil(45 × L ÷ 100) with the default role file. */
    fun quantaForLevel(level: Int, atLevel100: Double, multiplier: Double = 1.0): Float {
        val base = ceil(atLevel100 * level.coerceAtLeast(0) / 100.0)
        if (base.isNaN() || base <= 0.0) return 0f
        val scaled = if (multiplier == 1.0) base.toInt() else (base * multiplier).roundToInt()
        return scaled.coerceIn(0, MAX_QUANTA).toFloat()
    }

    /** The Quanta formula's number from the Muse's role file. A file that leaves it out, or gives the wrong type, takes 45. */
    fun quantaAtLevel100(params: JsonObject): Double = number(params, QUANTA_AT_LEVEL_100) ?: DEFAULT_QUANTA_AT_LEVEL_100

    /** CD-MUS-07: Quanta and no bookshelves with Apothic Enchanting, and bookshelves and no Quanta without it. */
    fun bonus(level: Int, params: JsonObject, apothicEnchanting: Boolean, multiplier: Double = 1.0): Bonus = if (apothicEnchanting) {
        Bonus(0, quantaForLevel(level, quantaAtLevel100(params), multiplier))
    } else {
        Bonus(forLevel(level, atLevel100(params), multiplier), 0f)
    }

    /**
     * CD-MUS-04: the real count plus the Muse's, at most 15. With no Muse, the count stays exactly as vanilla passes it, and a count
     * above 15 from another mod's shelves is never lowered.
     */
    @JvmStatic
    fun capped(real: Int, bonus: Int): Int = if (bonus <= 0 || real >= MAX_BOOKSHELVES) real else minOf(MAX_BOOKSHELVES, real + bonus)

    /**
     * CD-FIX-06: as [capped], at most the station's own [limit] too, such as an infuser's `getEnchantmentPowerLimit()`. A power at or
     * above the cap stays as it is.
     */
    @JvmStatic
    fun capped(real: Int, bonus: Int, limit: Int): Int {
        val cap = minOf(MAX_BOOKSHELVES, limit)
        return if (bonus <= 0 || real >= cap) real else minOf(cap, real + bonus)
    }

    /**
     * CD-FIX-06: the level that a player chose at an infuser, once a Muse's start or stop changed the power to one that [offered] the
     * highest level of. A higher level drops to that, but never below the item's [own] level, so the item keeps its enchantments.
     */
    @JvmStatic
    fun clampedLevel(chosen: Int, offered: Int, own: Int): Int = if (chosen <= offered || chosen <= own) chosen else maxOf(offered, own)

    /** Whether [a] and [b] hold the same enchantments at the same levels. Their tooltip flags may differ. */
    @JvmStatic
    fun sameLevels(a: ItemEnchantments, b: ItemEnchantments): Boolean =
        a.size() == b.size() && a.keySet().all { a.getLevel(it) == b.getLevel(it) }

    /**
     * CD-FIX-06: with Apothic Enchanting, an infuser counts the Muse's power as Eterna, and the Muse gives only Quanta (CD-MUS-07),
     * so no infuser is the Muse's target then. CD-MCH-06: CEI's Infuser reads only the stats of Apothic Enchanting, and exists only
     * with that mod, so it is the Muse's target only then.
     */
    fun takesStation(station: ResourceLocation, apothicEnchanting: Boolean): Boolean = when {
        station.namespace == INFUSER -> !apothicEnchanting
        station == CEI_INFUSER -> apothicEnchanting
        else -> true
    }

    /** The bookshelves of the Muse that Works at [table], 0 when none does or with Apothic Enchanting. The menu mixin calls this. */
    @JvmStatic
    fun of(level: Level, table: BlockPos): Int = at(level, table).bookshelves

    /** The Quanta of the Muse that Works at [table], 0 when none does. The stats mixin of Apothic Enchanting calls this on both sides. */
    @JvmStatic
    fun quantaOf(level: LevelReader, table: BlockPos): Float = at(level, table).quanta

    /**
     * The bonus of the Muse that Works at [table], or at a station above it whose mod gathers its stats there, such as CEI's Infuser
     * (CD-MCH-06). The engine lives on the server thread, so a client level or another thread gets no bonus.
     */
    private fun at(level: LevelReader, table: BlockPos): Bonus = guarded(EnchantingHandler, NONE) {
        val server = level as? ServerLevel ?: return@guarded NONE
        if (!server.server.isSameThread) return@guarded NONE
        val engine = WorkEngine.get() ?: return@guarded NONE
        workingAt(server, engine, table)
            ?: MuseStations.above(table).firstNotNullOfOrNull { station ->
                workingAt(server, engine, station)?.takeIf {
                    MuseStations.gathersBelow(BuiltInRegistries.BLOCK.getKey(server.getBlockState(station).block), station.y - table.y)
                }
            }
            ?: NONE
    }

    /** The bonus of the Muse whose work claim holds [pos] while it Works, or null. */
    private fun workingAt(server: ServerLevel, engine: WorkEngine, pos: BlockPos): Bonus? {
        val claim = engine.claims.holderOf(TargetKey.block(server.dimension().location(), pos))
        if (claim?.kind != ClaimKind.WORK || engine.worker(claim.worker)?.status != WorkerStatus.WORKING) return null
        val role = engine.roleOf(claim.worker)?.takeIf { it.handler === EnchantingHandler } ?: return null
        val pokemon = engine.pokemonOf(claim.worker) ?: return null
        return bonus(pokemon.level, role.definition.params, withApothicEnchanting, engine.multiplierOf(claim.worker))
    }

    private fun number(params: JsonObject, key: String): Double? =
        params.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble
}
