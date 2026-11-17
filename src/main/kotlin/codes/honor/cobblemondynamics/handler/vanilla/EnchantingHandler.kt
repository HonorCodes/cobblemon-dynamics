package codes.honor.cobblemondynamics.handler.vanilla

import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.role.RoleRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.TagKey
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * The Muse's target: a block of `#cobblemon_dynamics:muse_targets`, the vanilla enchanting table, Easy Magic's table, both infusers
 * of Enchanting Infuser and CEI's Infuser (CD-MUS-01, CD-FIX-05, CD-FIX-06, CD-MCH-06). A Working Muse adds its bookshelves or Quanta
 * through [MuseBonus], which the station's mixin or the stats hook reads when it computes its offers, its power or its stats, so the
 * handler has no work of its own on each tick.
 */
object EnchantingHandler : TargetHandler {
    override val id: ResourceLocation = id("enchanting")

    /** The Muse is leash only (CD-MUS-02), so no scan looks for its targets. */
    override val scanKind: ScanKind = ScanKind.NONE

    private val TARGETS: TagKey<Block> = TagKey.create(Registries.BLOCK, id("muse_targets"))

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean = isStation(state)

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = isStation(level.getBlockState(canonical))

    /**
     * A block of the tag whose own mixin applied, as the role gate read it, so a station whose hook a later version of its mod broke
     * turns off alone (CD-OPT-09). With Apothic Enchanting, no infuser (CD-FIX-06).
     */
    private fun isStation(state: BlockState): Boolean {
        if (!state.`is`(TARGETS)) return false
        val station = BuiltInRegistries.BLOCK.getKey(state.block)
        return RoleRegistry.targetOn(station.toString()) && MuseBonus.takesStation(station, MuseBonus.withApothicEnchanting)
    }

    /** CD-MUS-05: only while a player has this table's menu open. A station of [MuseStations] has its own demand (CD-MCH-06). */
    override fun hasDemand(ctx: WorkContext): Boolean {
        val station = MuseStations.of(BuiltInRegistries.BLOCK.getKey(ctx.level.getBlockState(ctx.target).block))
        return station?.hasDemand?.invoke(ctx.level, ctx.target) ?: EnchantingMenus.isOpenAt(ctx.level, ctx.target)
    }

    /** The open menus show the Muse's offers at once, also when the player put the item in before the Muse began. */
    override fun onStart(ctx: WorkContext) = EnchantingMenus.refresh(ctx.level, ctx.target)

    override fun applyWork(ctx: WorkContext) {}

    /** The engine has ended the Working state or the claim by now, so the offers drop back to the table's own bookshelves. */
    override fun onStop(ctx: WorkContext) = EnchantingMenus.refresh(ctx.level, ctx.target)
}
