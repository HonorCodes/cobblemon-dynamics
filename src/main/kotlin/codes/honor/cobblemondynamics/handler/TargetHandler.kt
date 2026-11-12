package codes.honor.cobblemondynamics.handler

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

    fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean

    /** The one position that keys the claim of a multi-block target (CD-CLM-04). */
    fun canonical(level: ServerLevel, pos: BlockPos): BlockPos = pos

    fun isPartOfTarget(level: ServerLevel, canonical: BlockPos, pos: BlockPos): Boolean = pos == canonical

    fun targetCenter(canonical: BlockPos): Vec3 = Vec3.atCenterOf(canonical)

    fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean

    fun hasDemand(ctx: WorkContext): Boolean

    fun onStart(ctx: WorkContext) {}

    fun applyWork(ctx: WorkContext)

    fun onStop(ctx: WorkContext) {}

    fun flip(level: ServerLevel, canonical: BlockPos): Boolean = false

    /** CD-TLK-12: whether a two-target binding of [role] may name [destination] for a pasture at [pasture]. */
    fun destinationInReach(role: RoleDefinition, pasture: BlockPos, destination: BlockPos): Boolean = true

    /** Whether a worker [bodyHeight] blocks high may work this target from [spot]. The engine asks only of a spot that passes its own checks. */
    fun allowsSpot(level: ServerLevel, canonical: BlockPos, spot: BlockPos, bodyHeight: Double): Boolean = true

    /** The lang key that refuses a Work Leash bind when the worker has no spot at the target, or null to bind and report later (CD-LSH-21). */
    val noSpotRefusal: String? get() = null

    /** A work claim on the context's target starts for its worker, or the claim follows the Pokémon to a new entity. */
    fun onClaim(ctx: WorkContext) {}

    /** The work claim of the context's worker ends, by any path, or leaves that entity for the Pokémon's new entity. */
    fun onUnclaim(ctx: WorkContext) {}

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
)

/**
 * One value that a handler keeps for the length of one work claim, such as a capability cache or the tick that its work started. The
 * engine gives each claim an empty memo and drops it when the claim ends, so nothing in it outlives the claim or the server.
 */
class ClaimMemo {
    var value: Any? = null
}
