package codes.honor.cobblemondynamics

import codes.honor.cobblemondynamics.handler.farming.CropAdapters
import codes.honor.cobblemondynamics.handler.farming.CropFacts
import codes.honor.cobblemondynamics.handler.farming.CropFactsReader
import codes.honor.cobblemondynamics.handler.farming.CultivatorHandler
import codes.honor.cobblemondynamics.handler.farming.FarmingTags
import codes.honor.cobblemondynamics.handler.farming.HarvesterHandler
import com.mojang.logging.LogUtils
import net.minecraft.core.BlockPos
import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.world.level.block.Block
import net.neoforged.neoforge.event.TagsUpdatedEvent
import org.slf4j.Logger
import java.util.TreeMap

/**
 * CD-CRP-05: when the server's tags load or reload, one INFO line for each farming role counts its tagged blocks by crop adapter and
 * names the tagged blocks that no adapter reads, from the facts of each block's default state. A pack maker reads them in the log.
 */
object FarmingLines {
    private val LOGGER: Logger = LogUtils.getLogger()
    private val log = LoadLog { LOGGER.info(it) }

    /** The client's copy of the tags posts this event too, so only a data load of the server logs. */
    fun onTagsUpdated(event: TagsUpdatedEvent) {
        if (event.updateCause != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) return
        val blocks = event.registryAccess.registryOrThrow(Registries.BLOCK)
        log.newLoad()
        log.info(line(blocks, CultivatorHandler.LANE, FarmingTags.CULTIVATOR_TARGETS, CropAdapters::growthAdapterName))
        log.info(line(blocks, HarvesterHandler.LANE, FarmingTags.HARVESTER_TARGETS, CropAdapters::harvestAdapterName))
    }

    private fun line(blocks: Registry<Block>, role: String, tag: TagKey<Block>, adapterOf: (CropFacts) -> String?): String {
        val byAdapter = TreeMap<String, Int>()
        val unadapted = ArrayList<ResourceLocation>()
        for (holder in blocks.getTagOrEmpty(tag)) {
            val block = holder.value()
            val adapter = adapterOf(CropFactsReader.read(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, block.defaultBlockState()))
            if (adapter != null) byAdapter.merge(adapter, 1, Int::plus) else blocks.getKey(block)?.let(unadapted::add)
        }
        return LoadLines.farming(role, byAdapter, unadapted)
    }
}
