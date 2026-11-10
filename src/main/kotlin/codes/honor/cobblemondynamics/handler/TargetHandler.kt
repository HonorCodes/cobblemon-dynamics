package codes.honor.cobblemondynamics.handler

import codes.honor.cobblemondynamics.role.RoleDefinition
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

/** What a pasture scan visits to find this handler's targets. */
enum class ScanKind { BLOCK_ENTITY, BLOCK, NONE }

/** How many targets one Work Leash binding of this handler's roles names. */
enum class BindingShape { SINGLE, SOURCE_AND_DESTINATION }

/** Finds, checks and works one kind of target. Roles name a handler by [id]. */
interface TargetHandler {
    val id: ResourceLocation
    val scanKind: ScanKind
    val bindingShape: BindingShape get() = BindingShape.SINGLE

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
}

class WorkContext(
    val level: ServerLevel,
    val target: BlockPos,
    val worker: PokemonEntity,
    val pokemon: Pokemon,
    val role: RoleDefinition,
    val now: Long,
    val destination: BlockPos? = null,
    val sourceFace: Direction? = null,
    val destinationFace: Direction? = null,
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
