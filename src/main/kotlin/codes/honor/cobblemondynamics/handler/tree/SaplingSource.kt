package codes.honor.cobblemondynamics.handler.tree

import codes.honor.cobblemondynamics.OnceLogger
import net.minecraft.resources.ResourceLocation

/** CD-LUM-31, CD-LUM-33: the kind of sapling that a fell replants, from the tree itself when its drops hold none. */
object SaplingSource {
    /**
     * The first kind of this order whose item [isSapling] and whose block [survives] in the base cell: each item of [drops] in drop
     * order; the sapling of each leaf of [leafIds] by its loot table ([leafSapling]); then the item that [named] gives for [baseLogId]
     * and for each leaf, which [byId] looks up. When no item of the order is a sapling, it logs one line for [baseLogId] through
     * [warnings] and returns null. A sapling that only fails to survive logs nothing, because the tree has a kind.
     */
    fun <K> kind(
        drops: List<K>,
        leafIds: List<ResourceLocation>,
        baseLogId: ResourceLocation?,
        leafSapling: (ResourceLocation) -> K?,
        byId: (ResourceLocation) -> K?,
        isSapling: (K) -> Boolean,
        survives: (K) -> Boolean,
        warnings: OnceLogger,
    ): K? {
        val names = (listOfNotNull(baseLogId) + leafIds).asSequence().mapNotNull(::named).distinct().mapNotNull(byId)
        val order = drops.asSequence() + leafIds.asSequence().mapNotNull(leafSapling) + names
        var anySapling = false
        for (item in order) {
            if (!isSapling(item)) continue
            anySapling = true
            if (survives(item)) return item
        }
        if (!anySapling) {
            warnings.warn("no-sapling:$baseLogId") {
                "The Lumberjack replants no sapling after a fell of $baseLogId: its drops, its leaves' loot and its name give no sapling"
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
