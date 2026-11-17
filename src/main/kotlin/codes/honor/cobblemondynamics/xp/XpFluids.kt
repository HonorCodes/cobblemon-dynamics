package codes.honor.cobblemondynamics.xp

import com.mojang.logging.LogUtils
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.material.Fluid
import net.neoforged.neoforge.common.Tags
import net.neoforged.neoforge.event.TagsUpdatedEvent
import org.slf4j.Logger

/** CD-XPC-08: the fluid of the collectors' outputs, chosen again after each tag load of the server. */
object XpFluids {
    private val LOGGER: Logger = LogUtils.getLogger()

    /** The fluid that the outputs offer now, or null when no XP fluid is known. */
    @Volatile
    var current: XpFluid<Fluid>? = null
        private set

    /**
     * The client's copy of the tags posts this event too, so only a data load of the server chooses. The tag's flowing variants never
     * come first, because only the source fluids of `c:experience` count. When the outputs gain or lose their fluid, the loaded
     * collectors tell their neighbours, so a pipe sees the change.
     */
    fun onTagsUpdated(event: TagsUpdatedEvent) {
        if (event.updateCause != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) return
        val fluids = event.registryAccess.registryOrThrow(Registries.FLUID)
        val tagged = fluids.getTag(Tags.Fluids.EXPERIENCE).map { set -> set.map { it.value() } }.orElse(emptyList())
            .filter { it.isSource(it.defaultFluidState()) }
        val choice = XpFluidChoice.choose({ id -> fluids.getOptional(id).orElse(null) }, tagged)
        val before = current
        current = choice
        LOGGER.info(XpFluidChoice.line(choice, choice?.let { fluids.getKey(it.fluid) }))
        if ((before == null) != (choice == null)) ExperienceCollectorBlockEntity.invalidateLoaded()
    }
}
