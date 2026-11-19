package codes.honor.cobblemondynamics.handler.tree

import codes.honor.cobblemondynamics.OnceLogger
import net.minecraft.resources.ResourceLocation

/** CD-LUM-31, CD-LUM-33: the kind of sapling that a fell replants, from the tree's own leaves and log. */
object SaplingSource {
    /**
     * The first kind of this order whose item [isSapling] and whose block [survives] in the base cell: the sapling of each leaf of
     * [leafIds], the most common leaf first, by its loot table ([leafSapling]); then the item that [named] gives for each leaf and last
     * for [baseLogId], which [byId] looks up. The drops never decide the kind, so a stray sapling, such as one of a few oak leaves on a
     * spruce, never replants the wrong tree. When no item of the order is a sapling, it logs one line for [baseLogId] through
     * [warnings] and returns null. A sapling that only fails to survive logs nothing, because the tree has a kind.
     */
    fun <K> kind(
        leafIds: List<ResourceLocation>,
        baseLogId: ResourceLocation?,
        leafSapling: (ResourceLocation) -> K?,
        byId: (ResourceLocation) -> K?,
        isSapling: (K) -> Boolean,
        survives: (K) -> Boolean,
        warnings: OnceLogger,
    ): K? {
        val names = (leafIds + listOfNotNull(baseLogId)).asSequence().mapNotNull(::named).distinct().mapNotNull(byId)
        val order = leafIds.asSequence().mapNotNull(leafSapling) + names
        var anySapling = false
        for (item in order) {
            if (!isSapling(item)) continue
            anySapling = true
            if (survives(item)) return item
        }
        if (!anySapling) {
            warnings.warn("no-sapling:$baseLogId") {
                "The Lumberjack replants no sapling after a fell of $baseLogId: its leaves' loot and the names give no sapling"
            }
        }
        return null
    }

    /** The name rule: `ns:x_sapling` for a block id `ns:x_log` or `ns:x_leaves`, else null. */
    fun named(id: ResourceLocation): ResourceLocation? {
        val stem = SUFFIXES.firstNotNullOfOrNull { suffix -> id.path.removeSuffix(suffix).takeIf { it != id.path && it.isNotEmpty() } }
        return stem?.let { ResourceLocation.fromNamespaceAndPath(id.namespace, "${it}_sapling") }
    }

    private val SUFFIXES = listOf("_log", "_leaves")
}
