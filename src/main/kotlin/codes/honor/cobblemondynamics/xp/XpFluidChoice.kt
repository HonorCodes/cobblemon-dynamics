package codes.honor.cobblemondynamics.xp

import net.minecraft.resources.ResourceLocation

/** CD-XPC-08: the fluid that the outputs of a collector offer, and its mB per point of XP. */
data class XpFluid<F : Any>(val fluid: F, val mbPerXp: Int)

/** CD-XPC-08: which fluid the outputs offer. It takes the fluids as values of any type, so the unit tests need no fluid registry. */
object XpFluidChoice {
    /** Create: Enchantment Industry's XP fluid, which `c:experience` does not hold (RES9). */
    val CEI_EXPERIENCE: ResourceLocation = ResourceLocation.fromNamespaceAndPath("create_enchantment_industry", "experience")

    const val CEI_MB_PER_XP = 1

    /** The ratio that NeoForge documents for the fluids of `c:experience`. */
    const val TAG_MB_PER_XP = 20

    /**
     * CEI's fluid at 1 mB per XP when [byId] knows it, otherwise the first fluid of [experienceTag] at 20 mB per XP, otherwise none.
     * The caller lists only the source fluids of the tag, so a flowing variant never comes first.
     */
    fun <F : Any> choose(byId: (ResourceLocation) -> F?, experienceTag: List<F>): XpFluid<F>? =
        byId(CEI_EXPERIENCE)?.let { XpFluid(it, CEI_MB_PER_XP) } ?: experienceTag.firstOrNull()?.let { XpFluid(it, TAG_MB_PER_XP) }

    /** The INFO line of a tag load, with [id] the id of the chosen fluid. */
    fun line(choice: XpFluid<*>?, id: ResourceLocation?): String = if (choice == null || id == null) {
        "Experience Collector: no XP fluid, so pipes drain nothing"
    } else {
        "Experience Collector: pipes drain $id at ${choice.mbPerXp} mB per XP"
    }
}
