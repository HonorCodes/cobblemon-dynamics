package codes.honor.cobblemondynamics.registry

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.bed.BedBlock
import codes.honor.cobblemondynamics.bed.BedBlockEntity
import codes.honor.cobblemondynamics.bed.BedTier
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.CreativeModeTabs
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor
import net.minecraft.world.level.material.PushReaction
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent
import net.neoforged.neoforge.registries.DeferredBlock
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredItem
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

/** The bed blocks, their items and their block entity type (CD-BED-01). */
object DynamicsBlocks {
    val BLOCKS: DeferredRegister.Blocks = DeferredRegister.createBlocks(MOD_ID)
    val ITEMS: DeferredRegister.Items = DeferredRegister.createItems(MOD_ID)
    val BLOCK_ENTITIES: DeferredRegister<BlockEntityType<*>> = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID)

    /** One block per tier, in tier order: `hay_bed`, `wool_bed`, `plush_bed` and `starlit_bed`. */
    val BEDS: Map<BedTier, DeferredBlock<BedBlock>> = BedTier.entries.associateWith { tier ->
        BLOCKS.registerBlock(tier.id, { BedBlock(tier, it) }, properties(tier))
    }

    val BED_ITEMS: Map<BedTier, DeferredItem<BlockItem>> = BEDS.mapValues { (_, block) -> ITEMS.registerSimpleBlockItem(block) }

    val BED: DeferredHolder<BlockEntityType<*>, BlockEntityType<BedBlockEntity>> = BLOCK_ENTITIES.register("bed", Supplier { bedType() })

    fun register(modBus: IEventBus) {
        BLOCKS.register(modBus)
        ITEMS.register(modBus)
        BLOCK_ENTITIES.register(modBus)
        modBus.addListener<BuildCreativeModeTabContentsEvent> { event ->
            if (event.tabKey == CreativeModeTabs.FUNCTIONAL_BLOCKS) BED_ITEMS.values.forEach { event.accept(it.get()) }
        }
    }

    private fun bedType(): BlockEntityType<BedBlockEntity> =
        BlockEntityType.Builder.of(::BedBlockEntity, *BEDS.values.map { it.get() }.toTypedArray()).build(null)

    /** Like a vanilla bed: soft, quick to break, not solid to light, and a piston breaks it. */
    private fun properties(tier: BedTier): BlockBehaviour.Properties = BlockBehaviour.Properties.of()
        .mapColor(
            when (tier) {
                BedTier.HAY -> MapColor.COLOR_YELLOW
                BedTier.WOOL -> MapColor.WOOL
                BedTier.PLUSH -> MapColor.COLOR_MAGENTA
                BedTier.STARLIT -> MapColor.COLOR_BLUE
            },
        )
        .sound(if (tier == BedTier.HAY) SoundType.GRASS else SoundType.WOOL)
        .strength(0.2f)
        .noOcclusion()
        .ignitedByLava()
        .pushReaction(PushReaction.DESTROY)
}
