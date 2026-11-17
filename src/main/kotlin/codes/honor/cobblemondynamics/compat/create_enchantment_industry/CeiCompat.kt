package codes.honor.cobblemondynamics.compat.create_enchantment_industry

import codes.honor.cobblemondynamics.MixinState
import codes.honor.cobblemondynamics.compat.AdapterNeeds
import codes.honor.cobblemondynamics.compat.CompatLines
import codes.honor.cobblemondynamics.handler.vanilla.MuseStations
import net.minecraft.resources.ResourceLocation

/**
 * The Create: Enchantment Industry integration: the Muse at the Infuser of CEI's Apothic Enchanting part (CD-MCH-06). The Infuser
 * exists only with Apothic Enchanting, whose stats hook adds the Muse's Quanta at the position that the Infuser gathers. The Muse's
 * handler takes the Infuser from `#cobblemon_dynamics:muse_targets`. The mod calls [register] only while CEI is loaded.
 */
object CeiCompat {
    private const val MOD = "create_enchantment_industry"
    private val INFUSER: ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD, "infuser")

    fun register() {
        CompatLines.register(MOD, "muse_infuser", AdapterNeeds(blocks = listOf(INFUSER), mixins = listOf(MixinState.APOTHIC_TABLE_STATS)))
        MuseStations.register(INFUSER, CeiInfuserTarget.STATION)
    }
}
