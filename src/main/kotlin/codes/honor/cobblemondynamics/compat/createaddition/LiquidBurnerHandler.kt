package codes.honor.cobblemondynamics.compat.createaddition

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.handler.create.HeatGate
import codes.honor.cobblemondynamics.id
import com.mrh0.createaddition.blocks.liquid_blaze_burner.LiquidBlazeBurnerBlockEntity
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.TagKey
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * The Superheater's target in Create Crafts & Additions: the Liquid Blaze Burner, a Blaze Burner that burns fluids (CD-MCH-01). The
 * worker grants the burner its heat for each tick of work: Kindled below level 50 and Seething from it, by the Superheater's own
 * params. The burner's mixin raises the heat of its own fuel to that heat, never lowers it, and leaves its fuel alone.
 */
object LiquidBurnerHandler : TargetHandler {
    override val id: ResourceLocation = id("liquid_burner")
    override val scanKind: ScanKind = ScanKind.BLOCK_ENTITY

    /** CD-FIX-07: the blocks above a burner that read its heat, beside Create's basin and fluid tank. */
    private val HEAT_USERS: TagKey<Block> = TagKey.create(Registries.BLOCK, id("superheater/heat_users"))

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        blockEntity is LiquidBlazeBurnerBlockEntity

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = burnerAt(level, canonical) != null

    /**
     * As the Blaze Burner's (CD-BRN-03, CD-FIX-07): a basin or a fluid tank above by the burner's own test, or a block of the heat users
     * tag there. A creative burner sets its own heat, so it has no demand. The mixin never lowers the heat of the burner's own fuel, so the
     * worker adds something only while its heat is above that one (R88).
     */
    override fun hasDemand(ctx: WorkContext): Boolean {
        val burner = burnerAt(ctx.level, ctx.target) ?: return false
        if (burner.isCreative) return false
        val heatUserAbove = ctx.level.getBlockState(ctx.target.above()).`is`(HEAT_USERS)
        if (!HeatGate.hasConsumer(burner.isValidBlockAbove, heatUserAbove)) return false
        val heat = heatOf(ctx) ?: return false
        val own = (burner as? LiquidBurnerAccess)?.`cobblemon_dynamics$ownHeat`() ?: return false
        return LiquidBurnerRule.adds(worker = heat.serializedName, own = own.serializedName)
    }

    override fun minLevelFor(ctx: WorkContext): Int {
        val burner = burnerAt(ctx.level, ctx.target) ?: return 1
        if (burner.isCreative) return 1
        val heatUserAbove = ctx.level.getBlockState(ctx.target.above()).`is`(HEAT_USERS)
        if (!HeatGate.hasConsumer(burner.isValidBlockAbove, heatUserAbove)) return 1
        val own = (burner as? LiquidBurnerAccess)?.`cobblemon_dynamics$ownHeat`()?.serializedName ?: return 1
        val low = HeatGate.heatFor(ctx.role.params, 1)
        if (low != null && LiquidBurnerRule.adds(worker = low, own = own)) return 1
        val min = HeatGate.minLevel(ctx.role.params).coerceAtLeast(1)
        val high = HeatGate.heatFor(ctx.role.params, min)
        return if (high != null && LiquidBurnerRule.adds(worker = high, own = own)) min else 1
    }

    /**
     * The heat follows the worker's level on every tick. The burner writes its block state, which basins read, only when its fuel
     * changes, so the handler asks it to write the heat again, which it does only when the heat changed.
     */
    override fun applyWork(ctx: WorkContext) {
        val burner = burnerAt(ctx.level, ctx.target) ?: return
        LiquidBurnerHeat.grant(burner, heatOf(ctx) ?: return, ctx.now)
        burner.updateBlockState()
    }

    /** The burner shows the heat of its own fuel again at once. In a chunk that is gone, the burner's mixin drops the lapsed heat. */
    override fun onStop(ctx: WorkContext) {
        if (!ctx.level.isLoaded(ctx.target)) return
        val burner = burnerAt(ctx.level, ctx.target) ?: return
        LiquidBurnerHeat.withdraw(burner)
        burner.updateBlockState()
    }

    private fun heatOf(ctx: WorkContext): BlazeBurnerBlock.HeatLevel? {
        val name = HeatGate.heatFor(ctx.role.params, ctx.pokemon.level) ?: return null
        return BlazeBurnerBlock.HeatLevel.entries.firstOrNull { it.serializedName == name }
    }

    private fun burnerAt(level: ServerLevel, pos: BlockPos): LiquidBlazeBurnerBlockEntity? =
        level.getBlockEntity(pos) as? LiquidBlazeBurnerBlockEntity
}
