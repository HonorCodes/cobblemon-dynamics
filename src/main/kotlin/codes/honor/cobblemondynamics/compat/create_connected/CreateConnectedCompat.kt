package codes.honor.cobblemondynamics.compat.create_connected

import codes.honor.cobblemondynamics.OnceLogger
import codes.honor.cobblemondynamics.compat.AdapterNeeds
import codes.honor.cobblemondynamics.compat.CompatLines
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.entity.BlockEntityType

/**
 * Create: Connected's Crank Wheel and Large Crank Wheel (CD-KIN-05). Their block entity extends Create's hand crank, so the Cranker's hand
 * crank handler already matches them, but Create attaches a behaviour by block entity type, and the drive went to the hand crank's type
 * alone. The Create integration gives the drive to the wheels' type too, which this object finds by id, so no source names a class of
 * Create: Connected. The mod calls [register] only while Create: Connected is loaded.
 */
object CreateConnectedCompat {
    const val MOD_ID = "create_connected"
    private val CRANK_WHEEL: ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD_ID, "crank_wheel")
    private val WHEELS: List<ResourceLocation> = listOf("crank_wheel", "large_crank_wheel").map {
        ResourceLocation.fromNamespaceAndPath(MOD_ID, it)
    }
    private val warnings = OnceLogger()

    fun register() {
        CompatLines.register(MOD_ID, "crank_wheel", AdapterNeeds(blocks = WHEELS))
    }

    /** The one block entity type of both crank wheels, or null with one warning when a later version renames it. */
    fun crankWheelType(): BlockEntityType<*>? {
        val type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(CRANK_WHEEL).orElse(null)
        if (type == null) {
            warnings.warn(MOD_ID) { "The Cranker cannot turn Create: Connected's crank wheels: no block entity type $CRANK_WHEEL" }
        }
        return type
    }
}
