package codes.honor.cobblemondynamics.handler.farming

import codes.honor.cobblemondynamics.OnceLogger
import com.google.gson.JsonObject
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import kotlin.math.abs

/** The area numbers of a farming role file (CD-CRP-25). */
data class AreaParams(val levelsPerExtraCrop: Int, val maxExtraCrops: Int) {
    companion object {
        private const val LEVELS = "levels_per_extra_crop"
        private const val MAX = "max_extra_crops"
        val DEFAULT = AreaParams(10, 10)

        /** `levels_per_extra_crop` (default 10) and `max_extra_crops` (default 10). A key of another type takes its default. */
        fun of(params: JsonObject): AreaParams = AreaParams(
            int(params, LEVELS) ?: DEFAULT.levelsPerExtraCrop,
            int(params, MAX) ?: DEFAULT.maxExtraCrops,
        )

        private fun int(params: JsonObject, key: String): Int? =
            params.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
    }
}

/**
 * The area of a farming use (R94, ADR-0015): the crops next to the target that one growth use or one harvest also works. The walk is
 * pure: each handler gives the checks of a cell and the work on it.
 */
object CropArea {
    private const val HORIZONTAL = 2
    private const val VERTICAL = 1
    private const val MAX_LEVEL = 100

    /**
     * CD-CRP-23: the 74 offsets within 2 blocks along x and z and 1 along y, less the target: the smaller squared distance first, then
     * the smaller absolute y, then the lower y, x and z. One list for the life of the server.
     */
    val offsets: List<Vec3i> = buildList {
        for (x in -HORIZONTAL..HORIZONTAL) {
            for (y in -VERTICAL..VERTICAL) {
                for (z in -HORIZONTAL..HORIZONTAL) if (x != 0 || y != 0 || z != 0) add(Vec3i(x, y, z))
            }
        }
    }.sortedWith(compareBy({ it.x * it.x + it.y * it.y + it.z * it.z }, { abs(it.y) }, { it.y }, { it.x }, { it.z }))

    /** CD-CRP-25, CD-PRG-03: N = min(floor(min(L, 100) / P), M), plus 1 for an Alpha worker, and 0 base when P is 0 or less. */
    fun count(level: Int, params: AreaParams, isAlpha: Boolean = false): Int {
        val base = if (params.levelsPerExtraCrop <= 0) {
            0
        } else {
            minOf(level.coerceIn(0, MAX_LEVEL) / params.levelsPerExtraCrop, params.maxExtraCrops).coerceAtLeast(0)
        }
        return base + if (isAlpha) 1 else 0
    }

    /**
     * CD-CRP-20, CD-CRP-24: works the first [n] cells of [offsets] around [target] that pass [eligible], in order. [eligible] reads a
     * cell in the tick just before [act] works it, and gives what [act] needs, or null to skip the cell. A cell whose [act] returns
     * false counts as not worked, and the next cell takes its place. Each cell is read at most once. A check or an act that throws, as
     * a block of another mod can, skips its cell only and reaches [failed], with what [eligible] gave before the work, or null when the
     * check threw. Returns the number of cells worked.
     */
    fun <T : Any> walk(
        target: BlockPos,
        n: Int,
        eligible: (BlockPos) -> T?,
        act: (BlockPos, T) -> Boolean,
        failed: (BlockPos, T?, Throwable) -> Unit = { _, _, _ -> },
    ): Int {
        if (n <= 0) return 0
        var worked = 0
        for (offset in offsets) {
            val cell = target.offset(offset)
            val found = attempt({ e -> failed(cell, null, e) }) { eligible(cell) } ?: continue
            if (attempt({ e -> failed(cell, found, e) }) { act(cell, found) } == true && ++worked >= n) break
        }
        return worked
    }

    /** CD-CUL-12: the Work Stamina ticks of one use tick, whatever number of crops it grew. */
    fun cost(anyUseReturnedTrue: Boolean, staminaTicksPerUse: Int): Int = if (anyUseReturnedTrue) staminaTicksPerUse.coerceAtLeast(0) else 0

    /**
     * CD-CRP-24: the cells that a use or a harvest of the crop at [cell] may change, which all lie inside the work area: the crop, the
     * cell above it, where an upper half or a rice panicle grows (CD-CUL-09), and the fruit at [fruit] of a gourd's stem.
     */
    fun changedCells(cell: BlockPos, fruit: BlockPos?): List<BlockPos> = listOfNotNull(cell, cell.above(), fruit)

    /** Runs [block], and gives null to [failed] for an `Exception` or a `LinkageError`, the throws of a block of another mod. */
    inline fun <R> attempt(failed: (Throwable) -> Unit, block: () -> R): R? = try {
        block()
    } catch (e: Exception) {
        failed(e)
        null
    } catch (e: LinkageError) {
        failed(e)
        null
    }
}

/**
 * The keys that the calls of one tick of one server took (CD-CRP-06). A call of a new tick or of another server starts an empty set, so
 * the set never holds more than the keys of one tick.
 */
class TickKeys<K : Any> {
    private var owner: Any? = null
    private var tick = Long.MIN_VALUE
    private val keys = HashSet<K>()

    /** Takes [key] in [tick] of [owner]. False when an earlier call of the same tick took it. */
    fun take(owner: Any, tick: Long, key: K): Boolean {
        sync(owner, tick)
        return keys.add(key)
    }

    fun isTaken(owner: Any, tick: Long, key: K): Boolean = owner === this.owner && tick == this.tick && key in keys

    private fun sync(owner: Any, tick: Long) {
        if (owner === this.owner && tick == this.tick) return
        keys.clear()
        this.owner = owner
        this.tick = tick
    }
}

/** The keys of one server's session. A call with another server starts an empty set, so nothing outlives the server. */
class SessionKeys<K : Any> {
    private var owner: Any? = null
    private val keys = HashSet<K>()

    /** Adds [key] for [owner]. True when it was new. */
    fun add(owner: Any, key: K): Boolean {
        sync(owner)
        return keys.add(key)
    }

    fun contains(owner: Any, key: K): Boolean = owner === this.owner && key in keys

    private fun sync(owner: Any) {
        if (owner === this.owner) return
        keys.clear()
        this.owner = owner
    }
}

/**
 * The server state of the areas, read on the server thread only: the cells that an area of each lane worked in this tick (CD-CRP-06),
 * and the blocks of each role whose check or work failed in an area, which the role's area leaves out until the server stops, with one
 * warning (CD-LUM-29).
 */
internal object AreaGuard {
    private data class Cell(val dimension: ResourceKey<Level>, val lane: String, val pos: Long)

    private val warnings = OnceLogger()
    private val worked = TickKeys<Cell>()
    private val denied = SessionKeys<String>()

    fun isWorked(level: ServerLevel, lane: String, pos: BlockPos): Boolean =
        worked.isTaken(level.server, level.server.tickCount.toLong(), Cell(level.dimension(), lane, pos.asLong()))

    /** Takes the cell at [pos] for this tick. False when an area of [lane] already worked it in this tick. */
    fun take(level: ServerLevel, lane: String, pos: BlockPos): Boolean =
        worked.take(level.server, level.server.tickCount.toLong(), Cell(level.dimension(), lane, pos.asLong()))

    fun isDenied(level: ServerLevel, role: String, block: Block): Boolean = denied.contains(level.server, key(role, block))

    /** A check or the work of [block], read before the work, threw at [pos]: the role's area leaves the block out from now on. */
    fun failed(level: ServerLevel, role: String, block: Block, pos: BlockPos, e: Throwable) {
        val key = key(role, block)
        denied.add(level.server, key)
        warnings.warn("area:$key") { "The $role leaves $key out of its area, because its check or work failed at $pos: $e" }
    }

    /** The growth use of a target of [block] threw, and the target counts as unchanged. */
    fun targetFailed(role: String, block: Block, pos: BlockPos, e: Throwable) {
        val key = key(role, block)
        warnings.warn("target:$key") { "The $role skips a growth use of $key at $pos, which fails: $e" }
    }

    /** The state at [pos] for a warning of a check that threw, before any work, or air in a chunk that is not loaded. */
    fun blockAt(level: ServerLevel, pos: BlockPos): BlockState =
        if (level.isLoaded(pos)) level.getBlockState(pos) else Blocks.AIR.defaultBlockState()

    private fun key(role: String, block: Block): String = "$role:${BuiltInRegistries.BLOCK.getKey(block)}"
}
