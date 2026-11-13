package codes.honor.cobblemondynamics.handler.farming

import codes.honor.cobblemondynamics.id
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.level.block.Block

/** The block tags of the farming roles (ADR-0015). A pack extends each one with a datapack. */
object FarmingTags {
    /** The blocks that a Cultivator grows (CD-CUL-02, CD-CRP-01). */
    val CULTIVATOR_TARGETS: TagKey<Block> = TagKey.create(Registries.BLOCK, id("cultivator_targets"))

    /** The blocks that a Harvester harvests (CD-HRV-02, CD-CRP-02). */
    val HARVESTER_TARGETS: TagKey<Block> = TagKey.create(Registries.BLOCK, id("harvester_targets"))

    /** Cobblemon's apricorns, which no Harvester takes, also when a datapack adds one to its tag (CD-HRV-03). */
    val APRICORNS: TagKey<Block> = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("cobblemon", "apricorns"))

    /** The adapter tag of each harvest kind that has one, in the order of [HarvestKind] (CD-CRP-03). */
    val ADAPTERS: Map<HarvestKind, TagKey<Block>> = HarvestKind.entries
        .mapNotNull { kind -> kind.tag?.let { kind to TagKey.create(Registries.BLOCK, id(it)) } }
        .toMap()
}
