package codes.honor.cobblemondynamics.handler.farming

import codes.honor.cobblemondynamics.id
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.util.ExtraCodecs
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.storage.loot.LootTable
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.datamaps.DataMapType
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent

/** The value of the data map `cobblemon_dynamics:harvest_loot` for one block (CD-FRM-03). */
data class HarvestLoot(val lootTable: ResourceKey<LootTable>, val ageAfter: Int)

/**
 * The data map `cobblemon_dynamics:harvest_loot` (blocks): the loot table that a harvest rolls in place of the block's own, and the
 * `age` that the pick leaves. A Twilight Forest bush rolls its berry table and goes to age 2, while its own loot table drops the
 * bush (CD-FIX-03). NeoForge reads no condition at the top of a data map file, so each entry of another mod holds its own
 * `neoforge:mod_loaded` condition (CD-OPT-12). An entry of a block with no `age`, or with an `age_after` at or above the block's
 * highest age, gives no adapter, and the Harvester never picks a block whose entry names a table that did not load. The farming line
 * names both (CD-CRP-05).
 */
object HarvestLootMap {
    private val CODEC: Codec<HarvestLoot> = RecordCodecBuilder.create { i ->
        i.group(
            ResourceKey.codec(Registries.LOOT_TABLE).fieldOf("loot_table").forGetter(HarvestLoot::lootTable),
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("age_after").forGetter(HarvestLoot::ageAfter),
        ).apply(i, ::HarvestLoot)
    }

    val TYPE: DataMapType<Block, HarvestLoot> = DataMapType.builder(id("harvest_loot"), Registries.BLOCK, CODEC).build()

    fun register(modBus: IEventBus) {
        modBus.addListener<RegisterDataMapTypesEvent> { it.register(TYPE) }
    }
}
