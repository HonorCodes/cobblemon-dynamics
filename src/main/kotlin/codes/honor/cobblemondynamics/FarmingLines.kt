package codes.honor.cobblemondynamics

import codes.honor.cobblemondynamics.handler.farming.CropAdapters
import codes.honor.cobblemondynamics.handler.farming.CropFacts
import codes.honor.cobblemondynamics.handler.farming.CropFactsReader
import codes.honor.cobblemondynamics.handler.farming.CultivatorHandler
import codes.honor.cobblemondynamics.handler.farming.FarmingTags
import codes.honor.cobblemondynamics.handler.farming.HarvestLoot
import codes.honor.cobblemondynamics.handler.farming.HarvestLootMap
import codes.honor.cobblemondynamics.handler.farming.HarvesterHandler
import codes.honor.cobblemondynamics.handler.farming.PickerHandler
import codes.honor.cobblemondynamics.handler.farming.PickerRules
import com.mojang.logging.LogUtils
import net.minecraft.core.BlockPos
import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.storage.loot.LootTable
import net.neoforged.neoforge.event.TagsUpdatedEvent
import org.slf4j.Logger
import java.util.TreeMap

/**
 * CD-CRP-05: when the server's tags load or reload, one INFO line for each farming role counts its tagged blocks by crop adapter and
 * names the tagged blocks that no adapter reads, from the facts of each block's default state. The Harvester's line also names each
 * entry of the loot data map whose table did not load (CD-FRM-03). A pack maker reads them in the log.
 */
object FarmingLines {
    private val LOGGER: Logger = LogUtils.getLogger()
    private val log = LoadLog { LOGGER.info(it) }

    /**
     * The client's copy of the tags posts this event too, so only a data load of the server logs. NeoForge applies the data maps in
     * its own listener of this event, which runs before the listeners of mods, and the server's event holds the loot tables of the load.
     */
    fun onTagsUpdated(event: TagsUpdatedEvent) {
        if (event.updateCause != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) return
        val blocks = event.registryAccess.registryOrThrow(Registries.BLOCK)
        val tables = event.registryAccess.registry(Registries.LOOT_TABLE).orElse(null)
        val unknown = if (tables == null) emptyMap() else unknownTables(blocks.getDataMap(HarvestLootMap.TYPE), tables::containsKey)
        log.newLoad()
        log.info(line(blocks, CultivatorHandler.LANE, FarmingTags.CULTIVATOR_TARGETS, CropAdapters::growthAdapterName))
        log.info(line(blocks, HarvesterHandler.LANE, FarmingTags.HARVESTER_TARGETS, CropAdapters::harvestAdapterName, unknown))
        log.info(pickerLine(blocks))
    }

    /**
     * CD-PCK-05: the members of `#cobblemon_dynamics:picker_targets`: the apricorns, the other fruit that the Picker picks at its
     * largest `age`, the members with no `age`, and the refused ones, so a pack maker sees each block that is no apricorn.
     */
    private fun pickerLine(blocks: Registry<Block>): String {
        val members = blocks.getTagOrEmpty(FarmingTags.PICKER_TARGETS).mapNotNull { holder ->
            blocks.getKey(holder.value())?.let { it to PickerHandler.memberOf(holder.value()) }
        }
        val of = { kind: PickerRules.TagMember -> members.filter { it.second == kind }.map { it.first } }
        return LoadLines.picker(
            of(PickerRules.TagMember.APRICORN).size,
            of(PickerRules.TagMember.OTHER_FRUIT),
            of(PickerRules.TagMember.NO_AGE),
            of(PickerRules.TagMember.REFUSED),
        )
    }

    /**
     * CD-FRM-03: each entry of the loot data map whose table [loaded] does not hold, as its block's id with the table's id. A pick of
     * such a block rolls nothing, so the Harvester skips it.
     */
    internal fun unknownTables(
        entries: Map<ResourceKey<Block>, HarvestLoot>,
        loaded: (ResourceKey<LootTable>) -> Boolean,
    ): Map<ResourceLocation, ResourceLocation> = entries.entries
        .filterNot { loaded(it.value.lootTable) }
        .associate { (block, loot) -> block.location() to loot.lootTable.location() }

    private fun line(
        blocks: Registry<Block>,
        role: String,
        tag: TagKey<Block>,
        adapterOf: (CropFacts) -> String?,
        unknownTables: Map<ResourceLocation, ResourceLocation> = emptyMap(),
    ): String {
        val byAdapter = TreeMap<String, Int>()
        val unadapted = ArrayList<ResourceLocation>()
        for (holder in blocks.getTagOrEmpty(tag)) {
            val block = holder.value()
            val adapter = adapterOf(CropFactsReader.read(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, block.defaultBlockState()))
            if (adapter != null) byAdapter.merge(adapter, 1, Int::plus) else blocks.getKey(block)?.let(unadapted::add)
        }
        return LoadLines.farming(role, byAdapter, unadapted, unknownTables)
    }
}
