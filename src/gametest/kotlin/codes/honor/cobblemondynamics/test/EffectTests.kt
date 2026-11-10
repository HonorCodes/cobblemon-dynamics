package codes.honor.cobblemondynamics.test

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.fx.JarParticleCatalog
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.role.RoleRegistry
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class EffectTests {
    /** CD-FX-11 against the real Cobblemon jar: the Stoker's particle ids are in its particle files, and an unknown id is not. */
    @GameTest(template = "floor_9x5x9")
    fun cdFx11CatalogReadsCobblemonParticles(helper: GameTestHelper) {
        val stoker = RoleRegistry.current.get(id("stoker"))?.definition ?: throw GameTestAssertException("no Stoker role")
        for (particle in stoker.effect.actorParticles + stoker.effect.targetParticles) {
            helper.assertTrue(JarParticleCatalog.contains(particle), "$particle is not in the loaded Cobblemon")
        }
        val unknown = ResourceLocation.fromNamespaceAndPath("cobblemon", "no_such_particle")
        helper.assertFalse(JarParticleCatalog.contains(unknown), "the catalog holds $unknown")
        helper.succeed()
    }
}
