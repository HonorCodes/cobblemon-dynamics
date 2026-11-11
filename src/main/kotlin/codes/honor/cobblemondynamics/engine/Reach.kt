package codes.honor.cobblemondynamics.engine

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.Vec3

/** The path check of a work spot (CD-ASG-09). */
object Reach {
    /**
     * The check of one claim by [entity]. It asks `createPath(spot, 0)`, the call that Cobblemon's walk to the spot makes later, and
     * Cobblemon's pathfinder knows how the species walks, swims or flies.
     */
    fun of(entity: PokemonEntity): Check =
        Check(entity.position(), entity.getAttributeValue(Attributes.FOLLOW_RANGE)) { entity.navigation.createPath(it, 0)?.canReach() }

    /**
     * Whether a worker at [from] has a whole path to a spot. [plan] gives true for a whole path, false for a partial one, and null for
     * no path at all. A spot farther than [followRange] counts as reachable without a plan, because the pathfinder does not plan that
     * far, and the travel timeout (CD-ASG-13) still catches it. No path at all means that the navigation cannot plan now: vanilla's
     * `createPath` gives null with no search while `canUpdatePath()` is false, which Cobblemon makes true for most species only on
     * the ground, in a liquid, in flight or as a passenger. The check then refuses that spot and every spot after it, and sets
     * [cannotPlan], so the claim waits for the next try instead of a spot that may have no path.
     */
    class Check(private val from: Vec3, private val followRange: Double, private val plan: (BlockPos) -> Boolean?) : (BlockPos) -> Boolean {
        var cannotPlan = false
            private set

        override fun invoke(spot: BlockPos): Boolean {
            if (cannotPlan) return false
            if (from.distanceTo(Vec3.atBottomCenterOf(spot)) > followRange) return true
            val reaches = plan(spot)
            cannotPlan = reaches == null
            return reaches == true
        }
    }
}
