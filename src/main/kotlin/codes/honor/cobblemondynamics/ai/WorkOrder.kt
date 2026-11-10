package codes.honor.cobblemondynamics.ai

import net.minecraft.world.phys.Vec3

/** What the engine asks of a worker's Brain on this tick. [hold] is true once the worker has reached [spot]. */
data class WorkOrder(val spot: Vec3, val lookAt: Vec3?, val hold: Boolean, val sleep: Boolean = false)
