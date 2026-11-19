package codes.honor.cobblemondynamics.handler.farming

import codes.honor.cobblemondynamics.OnceLogger
import com.google.gson.JsonObject
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
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

    /** CD-CRP-25: N = min(floor(min(L, 100) / P), M), and 0 when P is 0 or less. */
    fun count(level: Int, params: AreaParams): Int {
        if (params.levelsPerExtraCrop <= 0) return 0
        return minOf(level.coerceIn(0, MAX_LEVEL) / params.levelsPerExtraCrop, params.maxExtraCrops).coerceAtLeast(0)
    }

    /**
     * CD-CRP-20, CD-CRP-24: works the first [n] cells of [offsets] around [target] that pass [eligible], in order. [eligible] reads a
     * cell in the tick just before [act] works it, and gives what [act] needs, or null to skip the cell. A cell whose [act] returns
     * false counts as not worked, and the next cell takes its place. Each cell is read at most once. A check or an act that throws, as
     * a block of another mod can, skips its cell only and reaches [failed]. Returns the number of cells worked.
     */
    fun <T : Any> walk(
        target: BlockPos,
        n: Int,
        eligible: (BlockPos) -> T?,
        act: (BlockPos, T) -> Boolean,
        failed: (BlockPos, Throwable) -> Unit = { _, _ -> },
    ): Int {
        if (n <= 0) return 0
        var worked = 0
        for (offset in offsets) {
            val cell = target.offset(offset)
            val found = attempt(cell, failed) { eligible(cell) } ?: continue
            if (attempt(cell, failed) { act(cell, found) } == true && ++worked >= n) break
        }
        return worked
    }

    /** CD-CUL-12: the Work Stamina ticks of one use tick, whatever number of crops it grew. */
    fun cost(anyUseReturnedTrue: Boolean, staminaTicksPerUse: Int): Int = if (anyUseReturnedTrue) staminaTicksPerUse.coerceAtLeast(0) else 0

    private inline fun <R> attempt(cell: BlockPos, failed: (BlockPos, Throwable) -> Unit, block: () -> R): R? = try {
        block()
    } catch (e: Exception) {
        failed(cell, e)
        null
    } catch (e: LinkageError) {
        failed(cell, e)
        null
    }
}

/** One warning for each role and block whose check or work fails in an area (CD-LUM-29), so the role stays on. */
internal object AreaWarnings {
    private val warnings = OnceLogger()

    fun failed(role: String, level: ServerLevel, cell: BlockPos, e: Throwable) {
        val block = if (level.isLoaded(cell)) BuiltInRegistries.BLOCK.getKey(level.getBlockState(cell).block).toString() else "?"
        warnings.warn("$role:$block") { "The $role skips the crops of $block in its area, whose check or work fails: $e" }
    }
}
