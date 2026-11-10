package codes.honor.cobblemondynamics.coretest

import codes.honor.cobblemondynamics.test.TestWorld
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod

const val CORE_TEST_MOD_ID = "cobblemon_dynamics_coretest"

/** Holds the GameTests of the run without Create and AE2 (`runGameTestServerCore`). */
@Mod(CORE_TEST_MOD_ID)
class CoreTestMod(@Suppress("UNUSED_PARAMETER") modBus: IEventBus) {
    init {
        TestWorld.register()
    }
}
