package codes.honor.cobblemondynamics.client

import codes.honor.cobblemondynamics.id
import net.minecraft.resources.ResourceLocation

/** The Work Mode button's sprite of each state (CD-ART-04), in the GUI sprite atlas. */
object WorkModeSprites {
    val ON: ResourceLocation = id("work_mode/on")
    val OFF: ResourceLocation = id("work_mode/off")
    val DISABLED: ResourceLocation = id("work_mode/disabled")
    val HOVER: ResourceLocation = id("work_mode/hover")

    /** A viewer who may not toggle sees the disabled sprite first, then a hovered button shows hover, then on or off. */
    fun of(workMode: Boolean, active: Boolean, hovered: Boolean): ResourceLocation = when {
        !active -> DISABLED
        hovered -> HOVER
        workMode -> ON
        else -> OFF
    }
}
