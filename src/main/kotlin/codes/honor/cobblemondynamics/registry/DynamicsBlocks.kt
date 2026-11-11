package codes.honor.cobblemondynamics.registry

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.bed.BedBlock
import codes.honor.cobblemondynamics.bed.BedBlockEntity
import codes.honor.cobblemondynamics.bed.BedTier
import codes.honor.cobblemondynamics.cell.ChargeCellBlock
import codes.honor.cobblemondynamics.cell.ChargeCellBlockEntity
import codes.honor.cobblemondynamics.cell.ChargeCellRules
import com.mojang.serialization.Codec
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.Registries
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.world.item.BlockItem
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor
import net.minecraft.world.level.material.PushReaction
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.capabilities.ICapabilityProvider
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent
import net.neoforged.neoforge.energy.IEnergyStorage
import net.neoforged.neoforge.registries.DeferredBlock
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredItem
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

/** The mod's blocks, their items and their block entity types: the beds (CD-BED-01) and the Charge Cell (CD-GEN-08). */
object DynamicsBlocks {
    val BLOCKS: DeferredRegister.Blocks = DeferredRegister.createBlocks(MOD_ID)
    val ITEMS: DeferredRegister.Items = DeferredRegister.createItems(MOD_ID)
    val BLOCK_ENTITIES: DeferredRegister<BlockEntityType<*>> = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID)
    val COMPONENTS: DeferredRegister.DataComponents = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MOD_ID)

    /** One block per tier, in tier order: `hay_bed`, `wool_bed`, `plush_bed` and `starlit_bed`. */
    val BEDS: Map<BedTier, DeferredBlock<BedBlock>> = BedTier.entries.associateWith { tier ->
        BLOCKS.registerBlock(tier.id, { BedBlock(tier, it) }, properties(tier))
    }

    val BED_ITEMS: Map<BedTier, DeferredItem<BlockItem>> = BEDS.mapValues { (_, block) -> ITEMS.registerSimpleBlockItem(block) }

    val BED: DeferredHolder<BlockEntityType<*>, BlockEntityType<BedBlockEntity>> = BLOCK_ENTITIES.register("bed", Supplier { bedType() })

    val CHARGE_CELL: DeferredBlock<ChargeCellBlock> = BLOCKS.registerBlock("charge_cell", ::ChargeCellBlock, chargeCellProperties())

    val CHARGE_CELL_ITEM: DeferredItem<BlockItem> = ITEMS.registerSimpleBlockItem(CHARGE_CELL)

    val CHARGE_CELL_TYPE: DeferredHolder<BlockEntityType<*>, BlockEntityType<ChargeCellBlockEntity>> = BLOCK_ENTITIES.register(
        "charge_cell",
        Supplier { BlockEntityType.Builder.of(::ChargeCellBlockEntity, CHARGE_CELL.get()).build(null) },
    )

    /** CD-GEN-11: the FE that a Charge Cell item carries, 0 to 50,000. Saved and synced, so the client's tooltip shows it. */
    val STORED_ENERGY: DeferredHolder<DataComponentType<*>, DataComponentType<Int>> =
        COMPONENTS.registerComponentType("stored_energy") {
            it.persistent(Codec.intRange(0, ChargeCellRules.CAPACITY)).networkSynchronized(ByteBufCodecs.VAR_INT)
        }

    fun register(modBus: IEventBus) {
        BLOCKS.register(modBus)
        ITEMS.register(modBus)
        BLOCK_ENTITIES.register(modBus)
        COMPONENTS.register(modBus)
        // CD-GEN-08: one store behind every face.
        modBus.addListener<RegisterCapabilitiesEvent> { event ->
            event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                CHARGE_CELL_TYPE.get(),
                ICapabilityProvider<ChargeCellBlockEntity, Direction?, IEnergyStorage> { cell, _ -> cell.storage },
            )
        }
    }

    private fun bedType(): BlockEntityType<BedBlockEntity> =
        BlockEntityType.Builder.of(::BedBlockEntity, *BEDS.values.map { it.get() }.toTypedArray()).build(null)

    /** As hard as a copper block. It drops itself whatever breaks it, so a break never loses FE (CD-GEN-11). */
    private fun chargeCellProperties(): BlockBehaviour.Properties = BlockBehaviour.Properties.of()
        .mapColor(MapColor.COLOR_ORANGE)
        .sound(SoundType.COPPER)
        .strength(3.0f, 6.0f)

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
