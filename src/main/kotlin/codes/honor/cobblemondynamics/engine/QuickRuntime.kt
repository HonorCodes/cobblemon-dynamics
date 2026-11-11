package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.config.EngineSettings
import codes.honor.cobblemondynamics.pasture.PastureKey
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.ceil

/** The block that an owner aimed at when it sent out a party Pokémon, kept until Cobblemon ends the send-out (CD-QWK-01). */
data class QuickAim(val owner: UUID, val dimension: ResourceLocation, val pos: BlockPos, val tick: Long)

/**
 * One Quick Work order (ADR-0013): a party Pokémon that its owner sent out at a target. It has one worker, a work area around the
 * target and no scan, and the engine forgets it when the order ends.
 */
class QuickRuntime(
    override val level: ServerLevel,
    val owner: UUID,
    /** The block that the owner aimed at. The leash counts from its box (CD-QWK-13). */
    val aimed: BlockPos,
    /** The target of the order's claim, which the permission checks ask about (CD-QWK-29). */
    val canonical: BlockPos,
    /** The game time of the start, from which the permission is asked again every 20 ticks (CD-QWK-29). */
    val startedAt: Long,
    /** The target's name at the start, for a message after the target breaks. */
    val targetName: Component,
    val slot: WorkerSlot,
    private val workArea: WorkArea,
) : WorkSite {
    val dimension: ResourceLocation = level.dimension().location()

    override val pastureKey: PastureKey? get() = null

    override val slots: MutableMap<UUID, WorkerSlot> = linkedMapOf(slot.worker.pokemonId to slot)

    override fun area(settings: EngineSettings): WorkArea = workArea

    companion object {
        /** The cells within [range] of [center]: the cells that `WorkSpots.find` reads for a target with that center. */
        fun areaAround(center: Vec3, range: Double): WorkArea {
            val r = ceil(range).toInt()
            return WorkArea(BlockPos.containing(center), r, r)
        }
    }
}
