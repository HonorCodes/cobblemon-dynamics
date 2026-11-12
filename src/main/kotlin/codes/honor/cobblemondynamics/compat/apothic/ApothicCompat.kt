package codes.honor.cobblemondynamics.compat.apothic

import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent

/**
 * Apothic Enchanting's table menu reads its offers from the table's stats, so the Muse adds Quanta there through the stats hook in
 * `mixin/compat/apothic` (CD-MUS-07). The mod calls [register] only while Apothic Enchanting is loaded, so this class loads only then.
 */
object ApothicCompat {
    private const val STATS = "dev.shadowsoffire.apothic_enchanting.table.EnchantmentTableStats"

    /**
     * Mixin applies the hook when the stats class loads, which can wait until a player opens a table, and the role registry reads
     * whether it applied when the role files load (CD-MUS-06). Common setup comes first, so it loads the class.
     */
    fun register(modBus: IEventBus) {
        modBus.addListener<FMLCommonSetupEvent> { it.enqueueWork(Runnable { load() }) }
    }

    /**
     * Loads without initializing, so no Apothic Enchanting code runs here. A class that cannot load leaves the hook out, and the role
     * registry then turns the Muse off with one warning.
     */
    private fun load(): Boolean = try {
        Class.forName(STATS, false, ApothicCompat::class.java.classLoader)
        true
    } catch (e: ClassNotFoundException) {
        false
    } catch (e: LinkageError) {
        false
    }
}
