package codes.honor.cobblemondynamics.pasture

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/** The Work Mode of one pasture, saved with Cobblemon's pasture block entity (CD-WMD-02). */
data class WorkModeData(val enabled: Boolean) {
    companion object {
        val OFF = WorkModeData(false)

        val CODEC: Codec<WorkModeData> = RecordCodecBuilder.create { i ->
            i.group(Codec.BOOL.fieldOf("enabled").forGetter(WorkModeData::enabled)).apply(i, ::WorkModeData)
        }
    }
}
