package codes.honor.cobblemondynamics.handler.farming

import codes.honor.cobblemondynamics.id
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.BlockTags
import net.minecraft.tags.TagKey
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState

/** The block tags of the farming roles (ADR-0015). A pack extends each one with a datapack. */
object FarmingTags {
    /** The blocks that a Cultivator grows (CD-CUL-02, CD-CRP-01). */
    val CULTIVATOR_TARGETS: TagKey<Block> = TagKey.create(Registries.BLOCK, id("cultivator_targets"))

    /** The blocks of the Cultivator's tag that its bone meal never grows, such as Mystical Agriculture's crops (CD-FIX-04). */
    val CULTIVATOR_NEVER: TagKey<Block> = TagKey.create(Registries.BLOCK, id("cultivator/never"))

    /** The blocks that a Harvester harvests (CD-HRV-02, CD-CRP-02). */
    val HARVESTER_TARGETS: TagKey<Block> = TagKey.create(Registries.BLOCK, id("harvester_targets"))

    /** The blocks that a Picker picks when their `age` is at its largest (CD-PCK-05). The default holds `#cobblemon:apricorns`. */
    val PICKER_TARGETS: TagKey<Block> = TagKey.create(Registries.BLOCK, id("picker_targets"))

    /** Cobblemon's apricorns, which no Harvester takes, also when a datapack adds one to its tag (CD-HRV-03). */
    val APRICORNS: TagKey<Block> = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("cobblemon", "apricorns"))

    /** The adapter tag of each harvest kind that has one, in the order of [HarvestKind] (CD-CRP-03). */
    val ADAPTERS: Map<HarvestKind, TagKey<Block>> = HarvestKind.entries
        .mapNotNull { kind -> kind.tag?.let { kind to TagKey.create(Registries.BLOCK, id(it)) } }
        .toMap()

    /** Dynamic Trees' rooty soil, which grows no crop, and whose bone meal grows the tree on it. */
    val ROOTY_SOIL: TagKey<Block> = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("dynamictrees", "rooty_soil"))

    /** Dynamic Trees' saplings, which need not be in `#minecraft:saplings`. */
    val DYNAMIC_SAPLINGS: TagKey<Block> = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("dynamictrees", "saplings"))

    /**
     * CD-CRP-24, CD-CUL-11: the blocks that grow trees, which no area of the Cultivator or the Harvester works: they grow only as a
     * target, so a tree never grows out of the work area or a land claim by an area's use.
     */
    val AREA_LEFT_OUT: List<TagKey<Block>> = listOf(BlockTags.SAPLINGS, DYNAMIC_SAPLINGS, ROOTY_SOIL)

    fun leftOutOfArea(state: BlockState): Boolean = AREA_LEFT_OUT.any(state::`is`)
}
