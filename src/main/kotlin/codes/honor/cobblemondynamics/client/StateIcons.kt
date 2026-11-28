package codes.honor.cobblemondynamics.client

import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.id
import net.minecraft.resources.ResourceLocation

/** The state icons of the panel rows and the head bars: 16 × 16 textures that differ in shape and colour (CD-UI-08). */
object StateIcons {
    val IDLE: ResourceLocation = texture("idle")
    val TRAVELING: ResourceLocation = texture("traveling")
    val WORKING: ResourceLocation = texture("working")
    val STANDBY: ResourceLocation = texture("standby")
    val TIRED: ResourceLocation = texture("tired")
    val SLEEPING: ResourceLocation = texture("sleeping")
    val HEALING: ResourceLocation = texture("healing")

    val ALL: List<ResourceLocation> = listOf(IDLE, TRAVELING, WORKING, STANDBY, TIRED, SLEEPING, HEALING)

    /** CD-UI-07, CD-PRG-16: seven icons for the nine states, because Slacking shares Standby and ToBed and RestingInPlace share Tired. */
    fun iconFor(status: WorkerStatus): ResourceLocation = when (status) {
        WorkerStatus.IDLE -> IDLE
        WorkerStatus.TRAVELING -> TRAVELING
        WorkerStatus.WORKING -> WORKING
        WorkerStatus.STANDBY, WorkerStatus.SLACKING -> STANDBY
        WorkerStatus.TO_BED, WorkerStatus.RESTING_IN_PLACE -> TIRED
        WorkerStatus.SLEEPING -> SLEEPING
        WorkerStatus.HEALING -> HEALING
    }

    private fun texture(name: String): ResourceLocation = id("textures/gui/state/$name.png")
}
