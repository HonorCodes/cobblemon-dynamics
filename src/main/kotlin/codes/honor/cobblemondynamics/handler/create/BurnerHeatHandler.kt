package codes.honor.cobblemondynamics.handler.create

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * The target of the Burner Keeper and the Superheater: a filled Create Blaze Burner. The worker's heat goes in through
 * [PokemonHeat], and Create's own tick writes it into the burner's block state, which basins and boilers read.
 */
object BurnerHeatHandler : TargetHandler {
    override val id: ResourceLocation = id("burner_heat")
    override val scanKind: ScanKind = ScanKind.BLOCK_ENTITY

    /** CD-BRN-02: an empty burner has no block entity, and heat on one would make it drop a filled burner, a second blaze. */
    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        blockEntity is BlazeBurnerBlockEntity && isFilled(state)

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = burnerAt(level, canonical) != null

    /**
     * CD-BRN-03: Create's own test for a basin or a fluid tank directly above, the blocks that use the heat. A creative burner's tick
     * returns before it reads the heat level, so a worker's heat never reaches its block state, and it has no demand.
     */
    override fun hasDemand(ctx: WorkContext): Boolean {
        val burner = burnerAt(ctx.level, ctx.target) ?: return false
        return !burner.isCreative() && burner.isValidBlockAbove && heatOf(ctx) != null
    }

    override fun onStart(ctx: WorkContext) = applyWork(ctx)

    /** CD-BRN-01, CD-SUP-01, CD-SUP-04: the heat follows the worker's level on every tick, so a level-up applies at once. */
    override fun applyWork(ctx: WorkContext) {
        val burner = burnerAt(ctx.level, ctx.target) ?: return
        PokemonHeat.grant(burner, heatOf(ctx) ?: return, ctx.now)
    }

    /** CD-BRN-06: the burner's next tick writes the heat of its own fuel. In a chunk that is gone, the grant lapses by itself. */
    override fun onStop(ctx: WorkContext) {
        if (!ctx.level.isLoaded(ctx.target)) return
        burnerAt(ctx.level, ctx.target)?.let(PokemonHeat::withdraw)
    }

    private fun heatOf(ctx: WorkContext): BlazeBurnerBlock.HeatLevel? {
        val name = HeatGate.heatFor(ctx.role.params, ctx.pokemon.level) ?: return null
        return BlazeBurnerBlock.HeatLevel.entries.firstOrNull { it.serializedName == name }
    }

    private fun burnerAt(level: ServerLevel, pos: BlockPos): BlazeBurnerBlockEntity? =
        (level.getBlockEntity(pos) as? BlazeBurnerBlockEntity)?.takeIf { isFilled(it.blockState) }

    private fun isFilled(state: BlockState): Boolean = BlazeBurnerBlock.getHeatLevelOf(state) != BlazeBurnerBlock.HeatLevel.NONE
}
