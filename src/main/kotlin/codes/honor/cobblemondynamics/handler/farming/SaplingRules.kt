package codes.honor.cobblemondynamics.handler.farming

import net.minecraft.resources.ResourceLocation

/** When the Cultivator grows a sapling (CD-LUM-23). */
object SaplingRules {
    /**
     * The saplings whose tree grows only from a complete 2 × 2 of them, which in Minecraft 1.21.1 is the dark oak alone. Bone meal on
     * such a sapling outside a 2 × 2 moves it to its last stage and then changes nothing. The Lumberjack of ADR-0017 reads the same
     * saplings from the tag `#cobblemon_dynamics:lumberjack/needs_four`.
     */
    val NEEDS_FOUR: Set<ResourceLocation> = setOf(ResourceLocation.withDefaultNamespace("dark_oak_sapling"))

    /** The lowest corner of each 2 × 2 that holds a cell, as x and z offsets from that cell. */
    private val SQUARES = listOf(0 to 0, 0 to -1, -1 to 0, -1 to -1)

    /**
     * Whether a sapling lies in a complete 2 × 2 of its kind. [same] tells whether the cell at an x and a z offset from the sapling
     * holds the same block. The four squares that hold the sapling are those that vanilla's tree grower tries.
     */
    fun inFullSquare(same: (dx: Int, dz: Int) -> Boolean): Boolean =
        SQUARES.any { (x, z) -> same(x, z) && same(x + 1, z) && same(x, z + 1) && same(x + 1, z + 1) }
}
