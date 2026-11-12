package codes.honor.cobblemondynamics.ftbtest

import codes.honor.cobblemondynamics.test.TestWorld
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod

const val FTB_TEST_MOD_ID = "cobblemon_dynamics_ftbtest"

/** Holds the GameTests of the run with FTB Chunks (`runGameTestServerFtbChunks`), which CI does not run. */
@Mod(FTB_TEST_MOD_ID)
class FtbChunksTestMod(@Suppress("UNUSED_PARAMETER") modBus: IEventBus) {
    init {
        TestWorld.register()
    }
}
