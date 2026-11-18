package codes.honor.cobblemondynamics.compattest

import codes.honor.cobblemondynamics.test.TestWorld
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod

const val COMPAT_TEST_MOD_ID = "cobblemon_dynamics_compattest"

/**
 * Holds the GameTests of the compat run (`runGameTestServerCompat`), which CI does not run: one for each adapter that writes another
 * mod's private state through a mixin, an accessor or reflection, on that mod's real jar.
 */
@Mod(COMPAT_TEST_MOD_ID)
class CompatTestMod(@Suppress("UNUSED_PARAMETER") modBus: IEventBus) {
    init {
        TestWorld.register()
    }
}
