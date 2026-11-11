package codes.honor.cobblemondynamics.mctest

import codes.honor.cobblemondynamics.test.TestWorld
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod

const val MC_TEST_MOD_ID = "cobblemon_dynamics_mctest"

/** Holds the GameTests of the run with MineColonies (`runGameTestServerMineColonies`), which CI does not run. */
@Mod(MC_TEST_MOD_ID)
class MineColoniesTestMod(@Suppress("UNUSED_PARAMETER") modBus: IEventBus) {
    init {
        TestWorld.register()
    }
}
