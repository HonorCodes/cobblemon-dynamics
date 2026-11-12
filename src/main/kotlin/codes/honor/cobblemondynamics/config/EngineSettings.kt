package codes.honor.cobblemondynamics.config

import net.minecraft.resources.ResourceLocation

/** An immutable snapshot of the server config. Pure engine code takes this instead of reading the config. */
data class EngineSettings(
    val horizontalRadius: Int = 8,
    val verticalRadius: Int = 6,
    val scanIntervalTicks: Int = 40,
    val maxBlockChecksPerTick: Int = 512,
    val travelTimeoutTicks: Int = 600,
    val retryWaitTicks: Int = 6_000,
    val standbyMoveTicks: Int = 200,
    val effectBroadcastRadius: Int = 64,
    val toggleCooldownTicks: Int = 10,
    val pendingSelectionTicks: Int = 600,
    val ticksPerLevel: Int = 3_600,
    val healerMinutesAtLevel1: Int = 1_200,
    val healerMinutesAtLevel100: Int = 120,
    val flushIntervalTicks: Int = 600,
    val bedFactors: List<Int> = listOf(2, 3, 4, 8),
    val healCost: Double = 0.25,
    val lowStaminaThreshold: Double = 0.25,
    val disabledRoles: Set<ResourceLocation> = emptySet(),
    val compatCobbleworkers: Boolean = true,
    val compatCobblebase: Boolean = true,
    val quickWorkEnabled: Boolean = true,
    val quickWorkTargetRange: Int = 16,
    val quickWorkLeashRange: Int = 16,
    val maxWorkersPerContainer: Int = 5,
) {
    companion object {
        val DEFAULT = EngineSettings()

        /** CD-CFG-14: Cobblemon teleports or recalls a pastured Pokémon outside its roam box. */
        fun effectiveRadius(configured: Int, maxWander: Int): Int = minOf(configured, maxWander)
    }
}
