package codes.honor.cobblemondynamics.pasture

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/** The Work Mode and Access Lock of one pasture, saved with Cobblemon's pasture block entity (CD-WMD-02, CD-TEAM-03). */
data class WorkModeData(val enabled: Boolean, val locked: Boolean = false) {
    companion object {
        val OFF = WorkModeData(enabled = false, locked = false)

        val CODEC: Codec<WorkModeData> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.BOOL.fieldOf("enabled").forGetter(WorkModeData::enabled),
                Codec.BOOL.optionalFieldOf("locked", false).forGetter(WorkModeData::locked),
            ).apply(i, ::WorkModeData)
        }
    }
}
