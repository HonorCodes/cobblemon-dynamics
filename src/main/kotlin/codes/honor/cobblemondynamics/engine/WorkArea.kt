package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.config.EngineSettings
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.world.level.ChunkPos
import kotlin.math.abs

/** The box of block offsets around a pasture block in which its workers find targets and work spots. */
data class WorkArea(val center: BlockPos, val horizontal: Int, val vertical: Int) {
    val minX: Int = center.x - horizontal
    val maxX: Int = center.x + horizontal
    val minY: Int = center.y - vertical
    val maxY: Int = center.y + vertical
    val minZ: Int = center.z - horizontal
    val maxZ: Int = center.z + horizontal

    fun contains(pos: BlockPos): Boolean =
        abs(pos.x - center.x) <= horizontal && abs(pos.z - center.z) <= horizontal && abs(pos.y - center.y) <= vertical

    /** The chunks of the lowest and the highest corner. */
    fun chunkRange(): Pair<ChunkPos, ChunkPos> = ChunkPos(SectionPos.blockToSectionCoord(minX), SectionPos.blockToSectionCoord(minZ)) to
        ChunkPos(SectionPos.blockToSectionCoord(maxX), SectionPos.blockToSectionCoord(maxZ))

    companion object {
        fun of(pasture: BlockPos, settings: EngineSettings, maxWander: Int): WorkArea = WorkArea(
            pasture,
            EngineSettings.effectiveRadius(settings.horizontalRadius, maxWander),
            EngineSettings.effectiveRadius(settings.verticalRadius, maxWander),
        )
    }
}
