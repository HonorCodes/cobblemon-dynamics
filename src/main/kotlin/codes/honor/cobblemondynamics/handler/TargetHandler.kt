package codes.honor.cobblemondynamics.handler

import codes.honor.cobblemondynamics.engine.WorkArea
import codes.honor.cobblemondynamics.role.RoleDefinition
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

/** What a pasture scan visits to find this handler's targets. */
enum class ScanKind { BLOCK_ENTITY, BLOCK, NONE }

/** How many targets one Work Leash binding of this handler's roles names. */
enum class BindingShape { SINGLE, SOURCE_AND_DESTINATION }

/**
 * Finds, checks and works one kind of target. Roles name a handler by [id]. No member names `PokemonEntity` in its signature, because
 * JUnit reflects on this interface and that class does not load without the game's transformers. A hook gets the worker in a
 * [WorkContext].
 */
interface TargetHandler {
    val id: ResourceLocation
    val scanKind: ScanKind
    val bindingShape: BindingShape get() = BindingShape.SINGLE

    /**
     * True for a logistics handler, which only moves items into or out of containers, so several workers can share one target with
     * no item lost or made. Its claims are shared claims, and its bindings take places instead of reserving (CD-CLM-10, CD-LSH-29).
     */
    val sharesTargets: Boolean get() = false

    /**
     * The lane of this handler's work claims and bindings, or null for the one exclusive claim of a target. A crop holds one claim in
     * each lane, so a Cultivator and a Harvester share it, and a binding reserves the crop only in its own lane (CD-CRP-06, CD-CRP-07).
     */
    val claimLane: String? get() = null

    /**
     * True for a handler whose auto-assigned pasture workers release their claim once the target has no demand, instead of waiting in
     * Standby, so that the next scan pass gives them the nearest target with demand. The farming roles go crop to crop (CD-CRP-10).
     */
    val releasesWhenDone: Boolean get() = false

    /** True for a handler whose primary action breaks or replaces the claimed target cell (CD-TEAM-08). */
    val breaksBlocks: Boolean get() = false

    /** The cell whose break permission must pass before claiming [canonical], or null when this target breaks no block (CD-TEAM-08). */
    fun breakTarget(level: ServerLevel, canonical: BlockPos): BlockPos? = canonical.takeIf { breaksBlocks }

    fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean

    /** The one position that keys the claim of a multi-block target (CD-CLM-04). */
    fun canonical(level: ServerLevel, pos: BlockPos): BlockPos = pos

    fun isPartOfTarget(level: ServerLevel, canonical: BlockPos, pos: BlockPos): Boolean = pos == canonical

    /** Whether [pos] is its own [canonical] cell. A handler whose [canonical] searches may answer first from a cheap check (CD-LSH-33). */
    fun resolvesToItself(level: ServerLevel, pos: BlockPos): Boolean = canonical(level, pos) == pos

    fun targetCenter(canonical: BlockPos): Vec3 = Vec3.atCenterOf(canonical)

    /**
     * CD-ASG-24: the cells that the target at [canonical] fills when it grows into a tree, such as a sapling's cell, its 2 × 2 or a
     * tree's trunk base. No work spot puts a worker's body in their columns, from their height up. Empty for a target that grows in place.
     */
    fun growthCells(level: ServerLevel, canonical: BlockPos): List<BlockPos> = emptyList()

    /**
     * CD-ASG-25: true for a handler whose work grows, fells or replants a tree at its target. When that work changes the target's block
     * from or to a sapling or a log, the engine frees each worker near the target whose body the change filled.
     */
    val growsTrees: Boolean get() = false

    fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean

    /**
     * Whether a Work Leash binding of this handler's roles keeps [canonical] (CD-LSH-08). Claims follow [stillValid], so a claim on a
     * broken target ends at once (CD-CLM-08). The farming handlers keep the empty cell of a harvested crop (CD-CRP-09).
     */
    fun bindingHolds(level: ServerLevel, canonical: BlockPos): Boolean = stillValid(level, canonical)

    fun hasDemand(ctx: WorkContext): Boolean

    fun onStart(ctx: WorkContext) {}

    fun applyWork(ctx: WorkContext)

    fun onStop(ctx: WorkContext) {}

    fun flip(level: ServerLevel, canonical: BlockPos): Boolean = false

    /** CD-TLK-12: whether a two-target binding of [role] may name [destination] for a pasture at [pasture]. */
    fun destinationInReach(role: RoleDefinition, pasture: BlockPos, destination: BlockPos): Boolean = true

    /** Whether a worker [bodyHeight] blocks high may work this target from [spot]. The engine asks only of a spot that passes its own checks. */
    fun allowsSpot(level: ServerLevel, canonical: BlockPos, spot: BlockPos, bodyHeight: Double): Boolean = true

    /**
     * Whether the context's worker has reached its [WorkContext.spot] although the engine's own rule (CD-ASG-11) says that it has not.
     * The engine asks only on a Traveling tick of such a worker, and puts an arrived worker on its spot, as after every arrival.
     * Tailwind accepts a worker that stands anywhere on its own windmill's sails, at or above the spot's height (CD-TLW-11).
     */
    fun acceptsArrival(ctx: WorkContext): Boolean = false

    /** The lang key that refuses a Work Leash bind when the worker has no spot at the target, or null to bind and report later (CD-LSH-21). */
    val noSpotRefusal: String? get() = null

    /**
     * The lang key that refuses a Work Leash bind of [role] to [canonical], or null to bind. The message takes the worker's name, as the
     * Lumberjack's refusal of a tree that it would never fell does (CD-LUM-20).
     */
    fun bindRefusal(level: ServerLevel, canonical: BlockPos, role: RoleDefinition): String? = null

    /**
     * CD-LSH-31, CD-LSH-35: whether a switch to [role] may take [canonical]: true when the handler would not refuse a bind there and
     * holds the cell valid, false when it would, and null while the answer reads a cell of a chunk that is not loaded.
     */
    fun switchAnswer(level: ServerLevel, canonical: BlockPos, role: RoleDefinition): Boolean? =
        bindRefusal(level, canonical, role) == null && stillValid(level, canonical)

    /** A work claim on the context's target starts for its worker, or the claim follows the Pokémon to a new entity. */
    fun onClaim(ctx: WorkContext) {}

    /** The work claim of the context's worker ends, by any path, or leaves that entity for the Pokémon's new entity. */
    fun onUnclaim(ctx: WorkContext) {}

    /** Whether a pasture worker on this claim may enter Slacking on a periodic roll (CD-PRG-14). */
    fun canSlack(ctx: WorkContext): Boolean = true

    /** The action-bar message after a [flip] that changed the target, or null for none. */
    fun flipMessage(level: ServerLevel, canonical: BlockPos): Component? = null
}

class WorkContext(
    val level: ServerLevel,
    val target: BlockPos,
    val worker: PokemonEntity,
    val pokemon: Pokemon,
    val role: RoleDefinition,
    val now: Long,
    /** The destination of the worker's two-target binding, given only for that binding's own target and role. */
    val destination: BlockPos? = null,
    /** The face that the player clicked on the target, which is the source of a two-target binding. Null for an auto-assigned worker. */
    val sourceFace: Direction? = null,
    val destinationFace: Direction? = null,
    /** The worker's pasture block, so that a handler finds the work area (CD-MAG-04). */
    val pasture: BlockPos? = null,
    /**
     * What the handler keeps between the ticks of this claim. A context outside a claim, as when the engine weighs a target for a
     * worker, has an empty memo of its own.
     */
    val memo: ClaimMemo = ClaimMemo(),
    /** The worker's work spot, which a harvest's drops face (CD-HRV-06). */
    val spot: BlockPos? = null,
    /** Drains the given ticks of the worker's Work Stamina capacity, beyond the drain of Working (CD-CUL-06). */
    val spend: (Int) -> Unit = {},
    /**
     * Asks the land claim checks in this tick whether the worker may change a block: the placer's pasture check for a pasture worker,
     * and the owner's checks for a Quick Worker (CD-HRV-13).
     */
    val mayChange: (BlockPos) -> Boolean = { true },
    /**
     * Asks each claim mod in this tick whether the player may break a block: the placer by UUID for a pasture worker, and the online
     * owner for a Quick Worker (CD-HRV-16).
     */
    val mayBreak: (BlockPos) -> Boolean = { true },
    /** The handler stopped its target's demand for this worker for the given ticks, which the engine skips as long (CD-CUL-09). */
    val stalled: (Long) -> Unit = {},
    /**
     * The handler stopped the demand of the cell at the position for this worker for the given ticks, as [stalled] does for the target.
     * An area job, such as the Freezer's, works cells beside its target, and the engine skips such a cell beyond the claim (CD-FRZ-11).
     */
    val stalledAt: (BlockPos, Long) -> Unit = { _, _ -> },
    /**
     * Whether a [stalled] or a [stalledAt] stopped the demand of the cell at the position for this worker. A target that the worker
     * skips after a travel timeout or with no work spot is not stalled.
     */
    val isStalled: (BlockPos) -> Boolean = { false },
    /**
     * The worker's work area: its pasture's, or its Quick Work order's. Null for a context with no site, where an area of a farming use
     * works no cell (CD-CRP-24).
     */
    val area: WorkArea? = null,
    /**
     * Whether no other worker's claim or binding holds the cell at the position in the lane of the context's role, for this worker
     * (CD-CRP-24, CD-CLM-11). False for a context with no claim registry.
     */
    val laneFree: (BlockPos) -> Boolean = { false },
    val multiplier: Double = 1.0,
    val isAlpha: Boolean = false,
)

/**
 * One value that a handler keeps for the length of one work claim, such as a capability cache or the tick that its work started. The
 * engine gives each claim an empty memo and drops it when the claim ends, so nothing in it outlives the claim or the server.
 */
class ClaimMemo {
    var value: Any? = null
}
