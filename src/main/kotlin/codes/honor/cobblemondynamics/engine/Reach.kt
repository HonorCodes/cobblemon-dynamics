package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.stamina.WorkTraits
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.getWaterAndLavaIn
import net.minecraft.core.BlockPos
import net.minecraft.tags.FluidTags
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.Vec3
import kotlin.math.max

/** The path check and size-scaled reach of a work spot (CD-ASG-09, CD-NAV-04). */
object Reach {
    private const val WIDTH_SCALE = 0.85
    private const val ALPHA_BONUS = 1.0

    fun sizeBonus(bbWidth: Double, isAlpha: Boolean): Double {
        val widthBonus = if (bbWidth.isFinite()) max(0.0, bbWidth - 1.0) * WIDTH_SCALE else 0.0
        return widthBonus + if (isAlpha) ALPHA_BONUS else 0.0
    }

    fun effectiveRange(roleRange: Double, bbWidth: Double, isAlpha: Boolean): Double = roleRange + sizeBonus(bbWidth, isAlpha)

    fun isAlpha(pokemon: Pokemon): Boolean = WorkTraits.isAlpha(pokemon.isAlpha, pokemon.aspects, pokemon.forcedAspects)

    fun canPlanNow(
        canSwimInWater: Boolean,
        onGround: Boolean,
        isFlying: Boolean = false,
        inWaterBox: Boolean = false,
        isInLiquid: Boolean = false,
        isInLava: Boolean = false,
        eyeInLava: Boolean = false,
        inLavaBox: Boolean = false,
        canSwimInLava: Boolean = false,
        isPassenger: Boolean = false,
    ): Boolean {
        if (canSwimInWater) {
            return inWaterBox || onGround || isFlying || isInLiquid || isPassenger
        }
        return !(isInLava || eyeInLava) || (inLavaBox && canSwimInLava) || isPassenger
    }

    /**
     * The check of one claim by [entity]. It asks `createPath(spot, 0)`, the call that Cobblemon's walk to the spot makes later, and
     * Cobblemon's pathfinder knows how the species walks, swims or flies.
     */
    fun of(entity: PokemonEntity): Check {
        val (inWaterBox, inLavaBox) = entity.level().getWaterAndLavaIn(entity.boundingBox)
        val ready = canPlanNow(
            canSwimInWater = entity.canSwimInWater(),
            onGround = entity.onGround(),
            isFlying = entity.isFlying(),
            inWaterBox = inWaterBox,
            isInLiquid = entity.isInLiquid,
            isInLava = entity.isInLava,
            eyeInLava = entity.isEyeInFluid(FluidTags.LAVA),
            inLavaBox = inLavaBox,
            canSwimInLava = entity.canSwimInLava(),
            isPassenger = entity.isPassenger,
        )
        return Check(
            entity.position(),
            entity.getAttributeValue(Attributes.FOLLOW_RANGE),
            initiallyBlocked = !ready,
        ) { entity.navigation.createPath(it, 0)?.canReach() }
    }

    /**
     * Whether a worker at [from] has a whole path to a spot.
     */
    class Check(
        private val from: Vec3,
        private val followRange: Double,
        initiallyBlocked: Boolean = false,
        private val plan: (BlockPos) -> Boolean?,
    ) : (BlockPos) -> Boolean {
        constructor(from: Vec3, followRange: Double, plan: (BlockPos) -> Boolean?) : this(from, followRange, false, plan)

        var cannotPlan = initiallyBlocked
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
