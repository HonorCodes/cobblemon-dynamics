package codes.honor.cobblemondynamics.fx

import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SleepEffectTest {
    @Test
    fun `CD-FX-10 the sleep loop plays Cobblemon's two sleep particles every 40 ticks`() {
        val ids = listOf("cobblemon:sleep_actor", "cobblemon:sleep_actorresidual").map(ResourceLocation::parse)
        assertEquals(ids, SleepEffect.PARTICLES)
        // Cobblemon's own sleep timeline emits both from the "middle" locator.
        assertEquals(listOf("middle"), SleepEffect.LOCATORS)
        assertEquals(listOf(7L, 47L, 87L), (0L..100L).filter { SleepEffect.shouldPlay(7, it) })
    }
}
