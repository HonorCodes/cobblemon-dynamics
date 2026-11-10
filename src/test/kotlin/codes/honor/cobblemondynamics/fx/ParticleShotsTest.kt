package codes.honor.cobblemondynamics.fx

import codes.honor.cobblemondynamics.role.EffectDefinition
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ParticleShotsTest {
    private val actor = ResourceLocation.parse("cobblemon:flamethrower_actor")
    private val target = ResourceLocation.parse("cobblemon:flamethrower_target")
    private val burst = ResourceLocation.parse("cobblemon:flamethrower_targetburst")
    private val effect = EffectDefinition(40, listOf("flamethrower"), listOf(actor), listOf(target, burst), null)

    @Test
    fun `CD-FX-04 target particles play at the focus and actor particles stream from the worker`() {
        assertEquals(
            listOf(ParticleShot.FromWorker(actor), ParticleShot.AtFocus(target), ParticleShot.AtFocus(burst)),
            ParticleShots.of(effect) { it },
        )
    }

    @Test
    fun `CD-FX-11 an unknown id is dropped from either list`() {
        assertEquals(listOf(ParticleShot.AtFocus(burst)), ParticleShots.of(effect) { ids -> ids.filter { it == burst } })
    }
}
