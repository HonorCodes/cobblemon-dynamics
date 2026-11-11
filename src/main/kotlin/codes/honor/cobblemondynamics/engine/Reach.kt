package codes.honor.cobblemondynamics.engine

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.Vec3

/** The path check of a work spot (CD-ASG-09). */
object Reach {
    /**
     * Whether [entity]'s own navigation finds a whole path to a spot. It asks `createPath(spot, 0)`, the call that Cobblemon's walk
     * to the spot makes later, and Cobblemon's pathfinder knows how the species walks, swims or flies. A spot farther than the entity's
     * follow range counts as reachable, because the pathfinder does not plan that far. So does a spot when the navigation gives no
     * path at all, which means that it cannot plan at the moment, for example while a swimmer is in the air. The travel timeout
     * (CD-ASG-13) still catches both.
     */
    fun of(entity: PokemonEntity): (BlockPos) -> Boolean {
        val range = entity.getAttributeValue(Attributes.FOLLOW_RANGE)
        return { spot ->
            entity.position().distanceTo(Vec3.atBottomCenterOf(spot)) > range || entity.navigation.createPath(spot, 0)?.canReach() ?: true
        }
    }
}
