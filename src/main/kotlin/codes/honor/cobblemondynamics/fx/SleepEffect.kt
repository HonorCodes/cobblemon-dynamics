package codes.honor.cobblemondynamics.fx

import net.minecraft.resources.ResourceLocation

/** The loop of a worker that lies down: Cobblemon's sleep particles, the ones its sleep status plays, and never a sound (CD-FX-10). */
object SleepEffect {
    const val LOOP_TICKS = 40

    val PARTICLES: List<ResourceLocation> =
        listOf(ResourceLocation.parse("cobblemon:sleep_actor"), ResourceLocation.parse("cobblemon:sleep_actorresidual"))

    /** The locator of Cobblemon's own sleep timeline, `statuses/sleep.json`. */
    val LOCATORS: List<String> = listOf("middle")

    /** On the first tick that the worker lies down, and every 40 ticks after it. */
    fun shouldPlay(since: Long, now: Long): Boolean = EffectClock.shouldPlay(since, now, LOOP_TICKS)
}
