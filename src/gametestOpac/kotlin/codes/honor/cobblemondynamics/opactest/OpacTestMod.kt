package codes.honor.cobblemondynamics.opactest

import codes.honor.cobblemondynamics.test.TestWorld
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod

const val OPAC_TEST_MOD_ID = "cobblemon_dynamics_opactest"

/** Holds the GameTests of the run with Open Parties and Claims (`runGameTestServerOpac`), which CI does not run. */
@Mod(OPAC_TEST_MOD_ID)
class OpacTestMod(@Suppress("UNUSED_PARAMETER") modBus: IEventBus) {
    init {
        TestWorld.register()
    }
}
