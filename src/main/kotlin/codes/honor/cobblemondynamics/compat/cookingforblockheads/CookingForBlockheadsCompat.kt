package codes.honor.cobblemondynamics.compat.cookingforblockheads

import codes.honor.cobblemondynamics.compat.AdapterNeeds
import codes.honor.cobblemondynamics.compat.CompatLines
import codes.honor.cobblemondynamics.handler.HandlerRegistry
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.DyeColor

/** The Stoker on the Oven of Cooking for Blockheads (CD-FUEL-09). The mod calls [register] only while Cooking for Blockheads is loaded. */
object CookingForBlockheadsCompat {
    /** The 21.1.24 jar has an oven of each dye color and no plain one. */
    private val OVENS: List<ResourceLocation> = DyeColor.entries.map {
        ResourceLocation.fromNamespaceAndPath("cookingforblockheads", "${it.serializedName}_oven")
    }

    fun register() {
        HandlerRegistry.register(CfbOvenHandler)
        CompatLines.register("cookingforblockheads", "cfb_oven", AdapterNeeds(blocks = OVENS))
    }
}
