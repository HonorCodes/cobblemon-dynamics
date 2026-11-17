package codes.honor.cobblemondynamics.compat.createaddition

import codes.honor.cobblemondynamics.handler.WorkGrants
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock
import net.minecraft.world.level.block.entity.BlockEntity

/** The heat that Working Superheaters give Liquid Blaze Burners on the current tick, which the burner's mixin reads. */
object LiquidBurnerHeat {
    private val grants = WorkGrants<BlazeBurnerBlock.HeatLevel>()

    fun grant(burner: BlockEntity, heat: BlazeBurnerBlock.HeatLevel, now: Long) = grants.grant(burner, heat, now)

    fun withdraw(burner: BlockEntity) = grants.withdraw(burner)

    fun reset() = grants.reset()

    /** The heat that a worker gives [burner] on the current tick, or null. Always null on a client. */
    @JvmStatic
    fun levelFor(burner: BlockEntity): BlazeBurnerBlock.HeatLevel? = grants.current(burner)

    /**
     * Whether [burner] shows a worker's Seething heat, [shown], that neither a grant nor its own fuel's heat [own] gives any more. The
     * burner's mixin asks this on the server only, for a burner that is not creative, and reads both heats through its shadows.
     */
    @JvmStatic
    fun isStale(burner: BlockEntity, shown: BlazeBurnerBlock.HeatLevel, own: BlazeBurnerBlock.HeatLevel): Boolean =
        LiquidBurnerRule.stale(granted = levelFor(burner) != null, shown = shown.serializedName, own = own.serializedName)
}
