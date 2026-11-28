package codes.honor.cobblemondynamics.stamina

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.config.EngineSettings
import com.cobblemon.mod.common.api.pokemon.experience.SidemodExperienceSource

/** Pure Work XP and passive Friendship progression rules (CD-PRG-11 to CD-PRG-13). */
object WorkProgression {
    const val XP_INTERVAL_TICKS = 1_200L
    const val FRIENDSHIP_INTERVAL_TICKS = 12_000L
    const val OWNER_RANGE = 16.0
    private const val OWNER_RANGE_SQR = OWNER_RANGE * OWNER_RANGE

    val EXPERIENCE_SOURCE = SidemodExperienceSource(MOD_ID)

    fun xpGrant(level: Int, maxPokemonLevel: Int, s: EngineSettings): Int =
        if (!s.workXpEnabled || level >= maxPokemonLevel) 0 else 4 + level.coerceAtLeast(0) / 15

    fun friendshipGain(hasMate: Boolean, ownerSameLevel: Boolean, ownerDistanceSqr: Double, s: EngineSettings): Int {
        if (!s.friendshipEnabled) return 0
        return if (hasMate || (ownerSameLevel && ownerDistanceSqr <= OWNER_RANGE_SQR)) 2 else 1
    }
}
