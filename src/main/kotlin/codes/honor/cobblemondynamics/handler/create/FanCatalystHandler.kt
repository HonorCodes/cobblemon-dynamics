package codes.honor.cobblemondynamics.handler.create

import codes.honor.cobblemondynamics.OnceLogger
import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult
import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * The Fan Catalyst: a Fire-, Water- or Ghost-type stands in the block in front of a Create encased fan's face, where a catalyst covers
 * the whole air current (CD-FAN-03). It holds its catalyst on the fan's [FanState] while it Works, and the fan worker mark for its
 * whole claim, so that no fan pushes it off its spot on the way in (CD-FAN-12).
 */
object FanCatalystHandler : TargetHandler {
    const val NO_ROOM = "cobblemon_dynamics.leash.no_room_in_front"
    private const val CATALYST = "catalyst"
    private val warnings = OnceLogger()

    override val id: ResourceLocation = id("fan_catalyst")

    /** Leash only (CD-FAN-02, CD-ASG-05). */
    override val scanKind: ScanKind = ScanKind.NONE

    /** CD-FAN-04 */
    override val noSpotRefusal: String = NO_ROOM

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        blockEntity is EncasedFanBlockEntity

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean = level.getBlockEntity(canonical) is EncasedFanBlockEntity

    /** CD-FAN-03: only the block in front of the face of a fan that faces sideways (ruling 2 of the Phase 2 plan). */
    override fun allowsSpot(level: ServerLevel, canonical: BlockPos, spot: BlockPos, bodyHeight: Double): Boolean =
        spot == front(level, canonical)

    override fun onClaim(ctx: WorkContext) = FanWorkers.mark(ctx.worker, ctx.target)

    override fun onUnclaim(ctx: WorkContext) = FanWorkers.clear(ctx.worker)

    /** CD-FAN-15: the fan spins, and its air current holds an item. */
    override fun hasDemand(ctx: WorkContext): Boolean {
        val fan = fanAt(ctx) ?: return false
        return fan.speed != 0f && FanItems.any(fan)
    }

    override fun onStart(ctx: WorkContext) = applyWork(ctx)

    /** Renews the catalyst on every Working tick, also on a fan whose block entity was replaced. */
    override fun applyWork(ctx: WorkContext) {
        val kind = kind(ctx) ?: return
        fanAt(ctx)?.let(FanState::of)?.grant(kind, ctx.now)
    }

    /** In a chunk that is gone, the grant lapses by itself. */
    override fun onStop(ctx: WorkContext) {
        if (ctx.level.isLoaded(ctx.target)) fanAt(ctx)?.let(FanState::of)?.withdraw()
    }

    /** CD-FAN-10, CD-SEC-10: only a driven fan, one that a Working catalyst holds, switches its mode. */
    override fun flip(level: ServerLevel, canonical: BlockPos): Boolean {
        val state = FanState.of(level.getBlockEntity(canonical))?.takeIf { it.catalyst != null } ?: return false
        state.flipMode()
        return true
    }

    /** CD-FAN-16: "Fan mode: Blasting" or "Fan mode: Smoking". */
    override fun flipMessage(level: ServerLevel, canonical: BlockPos): Component? =
        FanState.of(level.getBlockEntity(canonical))?.let { Component.translatable(it.fireMode.langKey) }

    private fun fanAt(ctx: WorkContext): EncasedFanBlockEntity? = ctx.level.getBlockEntity(ctx.target) as? EncasedFanBlockEntity

    private fun front(level: ServerLevel, canonical: BlockPos): BlockPos? {
        val fan = level.getBlockEntity(canonical) as? EncasedFanBlockEntity ?: return null
        val facing = fan.blockState.getOptionalValue(DirectionalKineticBlock.FACING).orElse(null) ?: return null
        return FanFront.of(canonical, facing)
    }

    private fun kind(ctx: WorkContext): FanCatalyst? {
        val name = ctx.role.params.get(CATALYST)?.takeIf { it.isJsonPrimitive }?.asString
        return FanCatalyst.parse(name) ?: null.also {
            warnings.warn(ctx.role.id) {
                "Role ${ctx.role.id} names no fan catalyst (fire, water or ghost), so its workers process nothing"
            }
        }
    }
}

object FanItems {
    /** Whether [fan]'s air current holds an item: an item entity that it caught, or an item on a belt or a depot under it. */
    fun any(fan: EncasedFanBlockEntity): Boolean {
        val current = (fan.airCurrent as Any) as? AirCurrentAccess ?: return false
        if (current.`cobblemon_dynamics$caughtEntities`().any { it is ItemEntity && it.isAlive && !it.item.isEmpty }) return true
        return current.`cobblemon_dynamics$affectedHandlers`().any { holdsItem(it.left) }
    }

    /** A visit that returns `doNothing()` leaves the items as they are: depots and belts skip a result that changed nothing. */
    private fun holdsItem(handler: TransportedItemStackHandlerBehaviour): Boolean {
        var found = false
        handler.handleProcessingOnAllItems { transported ->
            if (!transported.stack.isEmpty) found = true
            TransportedResult.doNothing()
        }
        return found
    }
}
