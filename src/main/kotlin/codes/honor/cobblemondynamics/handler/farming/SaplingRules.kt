package codes.honor.cobblemondynamics.handler.farming

import codes.honor.cobblemondynamics.id
import net.minecraft.core.registries.Registries
import net.minecraft.tags.TagKey
import net.minecraft.world.level.block.Block

/** When the Cultivator grows a sapling (CD-LUM-23). */
object SaplingRules {
    /**
     * The saplings whose tree grows only from a complete 2 × 2 of them: the Lumberjack's tag `#cobblemon_dynamics:lumberjack/needs_four`,
     * whose default holds the dark oak sapling. The Cultivator and the Lumberjack's replant (CD-LUM-18) read this one tag. Bone meal on
     * such a sapling outside a 2 × 2 moves it to its last stage and then changes nothing.
     */
    val NEEDS_FOUR: TagKey<Block> = TagKey.create(Registries.BLOCK, id("lumberjack/needs_four"))

    /** The lowest corner of each 2 × 2 that holds a cell, as x and z offsets from that cell. */
    private val SQUARES = listOf(0 to 0, 0 to -1, -1 to 0, -1 to -1)

    /**
     * Whether a sapling lies in a complete 2 × 2 of its kind. [same] tells whether the cell at an x and a z offset from the sapling
     * holds the same block. The four squares that hold the sapling are those that vanilla's tree grower tries.
     */
    fun inFullSquare(same: (dx: Int, dz: Int) -> Boolean): Boolean =
        SQUARES.any { (x, z) -> same(x, z) && same(x + 1, z) && same(x, z + 1) && same(x + 1, z + 1) }
}
