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
import kotlin.math.ceil

/** The bookshelves that a Muse adds to its enchanting table, which the enchanting-table mixin reads. */
object MuseBonus {
    /** Vanilla's offers stop growing at 15 bookshelves: `EnchantmentHelper.getEnchantmentCost` clamps its count there. */
    const val MAX_BOOKSHELVES = 15

    private const val AT_LEVEL_100 = "bookshelves_at_level_100"
    private const val DEFAULT_AT_LEVEL_100 = 15.0

    /** CD-MUS-03: ceil(`bookshelves_at_level_100` × L ÷ 100), so ceil(15 × L ÷ 100) with the default role file. */
    fun forLevel(level: Int, atLevel100: Double): Int {
        val raw = ceil(atLevel100 * level.coerceAtLeast(0) / 100.0)
        if (raw.isNaN() || raw <= 0.0) return 0
        return minOf(raw, MAX_BOOKSHELVES.toDouble()).toInt()
    }

    /** The formula's number from the Muse's role file. A file that leaves it out, or gives the wrong type, takes 15. */
    fun atLevel100(params: JsonObject): Double =
        params.get(AT_LEVEL_100)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble ?: DEFAULT_AT_LEVEL_100

    /**
     * CD-MUS-04: the real count plus the Muse's, at most 15. With no Muse, the count stays exactly as vanilla passes it, and a count
     * above 15 from another mod's shelves is never lowered.
     */
    @JvmStatic
    fun capped(real: Int, bonus: Int): Int = if (bonus <= 0 || real >= MAX_BOOKSHELVES) real else minOf(MAX_BOOKSHELVES, real + bonus)

    /** The bookshelves of the Muse that Works at [table], 0 when none does. The mixin calls this on the server only. */
    @JvmStatic
    fun of(level: Level, table: BlockPos): Int = guarded(EnchantingHandler, 0) {
        val server = level as? ServerLevel ?: return@guarded 0
        val engine = WorkEngine.get() ?: return@guarded 0
        val claim = engine.claims.holderOf(TargetKey.block(server.dimension().location(), table))
        if (claim?.kind != ClaimKind.WORK || engine.worker(claim.worker)?.status != WorkerStatus.WORKING) return@guarded 0
        val role = engine.roleOf(claim.worker)?.takeIf { it.handler === EnchantingHandler } ?: return@guarded 0
        val pokemon = engine.pokemonOf(claim.worker) ?: return@guarded 0
        forLevel(pokemon.level, atLevel100(role.definition.params))
    }
}
