package codes.honor.cobblemondynamics.compat.createaddition

import codes.honor.cobblemondynamics.handler.WorkGrants
import com.mrh0.createaddition.blocks.liquid_blaze_burner.LiquidBlazeBurnerBlockEntity
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

    /** Whether [burner] shows a worker's Seething heat, [shown], that neither a grant nor its own fuel's heat [own] gives any more. */
    @JvmStatic
    fun isStale(burner: LiquidBlazeBurnerBlockEntity, shown: BlazeBurnerBlock.HeatLevel, own: BlazeBurnerBlock.HeatLevel): Boolean {
        val level = burner.level ?: return false
        if (level.isClientSide || burner.isCreative) return false
        return LiquidBurnerRule.stale(granted = levelFor(burner) != null, shown = shown.serializedName, own = own.serializedName)
    }
}
