package codes.honor.cobblemondynamics.pasture

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.Optional

/** The Work Mode, Access Lock and optional horizontal radius of one pasture, saved with Cobblemon's pasture block entity (CD-WMD-02, CD-TEAM-03, CD-SHF-02). */
data class WorkModeData(val enabled: Boolean, val locked: Boolean = false, val radius: Int? = null) {
    companion object {
        val RADIUS_STEPS: List<Int> = listOf(4, 8, 12, 16)
        val OFF = WorkModeData(enabled = false, locked = false, radius = null)

        val CODEC: Codec<WorkModeData> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.BOOL.fieldOf("enabled").forGetter(WorkModeData::enabled),
                Codec.BOOL.optionalFieldOf("locked", false).forGetter(WorkModeData::locked),
                Codec.INT.optionalFieldOf("radius").forGetter { Optional.ofNullable(it.radius) },
            ).apply(i) { enabled, locked, radius ->
                WorkModeData(enabled, locked, radius.orElse(null))
            }
        }
    }
}
