package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.CobblemonDynamics
import codes.honor.cobblemondynamics.ai.BrainBridge
import codes.honor.cobblemondynamics.ai.WorkOrder
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.claim.ClaimRegistry
import codes.honor.cobblemondynamics.claim.ClaimResult
import codes.honor.cobblemondynamics.claim.TargetKey
import codes.honor.cobblemondynamics.config.EngineSettings
import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.pasture.PastureAdapter
import codes.honor.cobblemondynamics.pasture.PastureKey
import codes.honor.cobblemondynamics.pasture.TetheredPokemon
import codes.honor.cobblemondynamics.registry.DynamicsAttachments
import codes.honor.cobblemondynamics.role.ActiveRole
import codes.honor.cobblemondynamics.role.RoleDefinition
import codes.honor.cobblemondynamics.role.RoleRegistry
import codes.honor.cobblemondynamics.role.RoleSelection
import codes.honor.cobblemondynamics.role.RoleSet
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

/**
 * The server's work engine: the workers of every pasture in Work Mode, their claims and their states. Runs on the server
 * thread only, and holds nothing that a restart keeps (CD-CLM-07).
 */
class WorkEngine private constructor(val server: MinecraftServer) {
    /** The Work Leash adds reservations. Until then no target is reserved. */
    val claims = ClaimRegistry { null }

    private val pastures = LinkedHashMap<PastureKey, PastureRuntime>()
    private val slots = HashMap<UUID, PastureRuntime.Slot>()

    /** The species already warned about, because their Brains cannot hold a work order. */
    private val unbridged = HashSet<ResourceLocation>()

    fun worker(pokemonId: UUID): Worker? = slots[pokemonId]?.worker

    fun entityOf(pokemonId: UUID): PokemonEntity? = slots[pokemonId]?.entity?.takeUnless { it.isRemoved }

    /** One engine tick of [level], after the level ticked its block entities and entities. */
    fun tickLevel(level: ServerLevel) {
        val dimension = level.dimension().location()
        val indexed = level.getExistingDataOrNull(DynamicsAttachments.WORK_PASTURES)?.all().orEmpty()
        if (indexed.isEmpty() && pastures.keys.none { it.dimension == dimension }) return
        val t = tick(level)
        for (pos in indexed) {
            val key = PastureKey(dimension, pos)
            pastures.getOrPut(key) { PastureRuntime(level, key, t.now, t.settings.scanIntervalTicks) }
        }
        for (runtime in runtimesOf(dimension)) if (runtime.key.pos !in indexed) removePasture(t, runtime, fromIndex = false)

        for (runtime in runtimesOf(dimension)) if (level.isLoaded(runtime.pos) && runtime.isPollDue(t.now)) poll(t, runtime)
        validateClaims(t)
        for (runtime in runtimesOf(dimension)) if (level.isLoaded(runtime.pos)) scan(t, runtime)
        for (runtime in runtimesOf(dimension)) for (slot in runtime.slots.values.toList()) tickWorker(t, runtime, slot)
        for (runtime in runtimesOf(dimension)) for (slot in runtime.slots.values.toList()) direct(t, slot)
    }

    fun onWorkModeChanged(level: ServerLevel, pasture: BlockPos, enabled: Boolean) {
        val key = PastureKey(level.dimension().location(), pasture.asLong())
        if (enabled) {
            pastures.getOrPut(key) { PastureRuntime(level, key, level.gameTime, WorkEngineSettings.current.scanIntervalTicks) }
            return
        }
        pastures[key]?.let { removePasture(tick(level), it, fromIndex = false) }
        // CD-WMD-05: bed and heal claims too, also those of workers that the engine no longer ticks.
        claims.releaseAll { it.pasture == key }
    }

    /** CD-CLM-08: every claim whose target sits in [chunk]. */
    fun onChunkUnload(level: ServerLevel, chunk: ChunkPos) {
        val t = tick(level)
        for (claim in claims.all()) {
            val key = claim.key as? TargetKey.Block ?: continue
            if (key.dimension != t.dimension || ChunkPos(BlockPos.of(key.pos)) != chunk) continue
            val slot = slots[claim.worker]
            if (slot != null) release(t, slot) else claims.release(claim.worker)
        }
    }

    /** Ends the claim of [pokemonId] through the release path: `onStop` if it is Working, then Idle. */
    fun release(pokemonId: UUID) {
        val slot = slots[pokemonId] ?: return
        val runtime = pastures[slot.worker.pasture] ?: return
        release(tick(runtime.level), slot)
    }

    /** The GameTest form of a restart. A restart also loses the Brain memories, because the work order has no codec. */
    fun resetRuntimeForTest() {
        for (slot in slots.values) BrainBridge.clear(slot.entity)
        claims.clear()
        pastures.clear()
        slots.clear()
    }

    private class Tick(val level: ServerLevel, val settings: EngineSettings, val roles: RoleSet) {
        val now: Long = level.gameTime
        val dimension: ResourceLocation = level.dimension().location()
        val machine = WorkerMachine(settings)
        val world: ScanWorld = LevelScanWorld(level)
    }

    private fun tick(level: ServerLevel) = Tick(level, WorkEngineSettings.current, RoleRegistry.current)

    private fun runtimesOf(dimension: ResourceLocation): List<PastureRuntime> = pastures.values.filter { it.key.dimension == dimension }

    /** Step 1: workers join and leave with the pasture's tethered list (CD-ASG-01, CD-ASG-15). A pasture that is gone leaves. */
    private fun poll(t: Tick, runtime: PastureRuntime) {
        runtime.markPolled()
        val be = PastureAdapter.pastureAt(t.level, runtime.pos)
        if (be == null || !PastureAdapter.isWorkMode(be)) {
            removePasture(t, runtime, fromIndex = true)
            return
        }
        val present = HashSet<UUID>()
        for (tethered in PastureAdapter.tethered(be)) {
            val pokemon = tethered.pokemon ?: continue
            val entity = tethered.entity ?: continue
            if (!canWork(t.level, tethered, pokemon, entity)) continue
            present += tethered.pokemonId
            val slot = runtime.slots[tethered.pokemonId]
            if (slot != null) {
                if (slot.entity !== entity) BrainBridge.clear(slot.entity)
                slot.entity = entity
                slot.pokemon = pokemon
            } else if (tethered.pokemonId !in slots) {
                val joined = PastureRuntime.Slot(Worker(tethered.pokemonId, runtime.key), entity, pokemon)
                runtime.slots[tethered.pokemonId] = joined
                slots[tethered.pokemonId] = joined
            }
        }
        for (slot in runtime.slots.values.toList()) if (slot.worker.pokemonId !in present) drop(t, runtime, slot)
    }

    /** Owned, not fainted, no battle clone, in this level, and tethered by its owner. */
    private fun canWork(level: ServerLevel, tethered: TetheredPokemon, pokemon: Pokemon, entity: PokemonEntity): Boolean =
        pokemon.isPlayerOwned() &&
            !pokemon.isFainted() &&
            !pokemon.isBattleClone() &&
            !entity.isRemoved &&
            entity.level() === level &&
            tethered.ownerId == pokemon.getOwnerUUID()

    /** Step 2: a work claim ends when its target is gone or invalid (CD-CLM-08), its role is gone, or no worker holds it. */
    private fun validateClaims(t: Tick) {
        for (claim in claims.all()) {
            val key = claim.key as? TargetKey.Block ?: continue
            if (claim.kind != ClaimKind.WORK || key.dimension != t.dimension) continue
            val slot = slots[claim.worker]
            if (slot == null) {
                claims.release(claim.worker)
                continue
            }
            val role = slot.role?.let { t.roles.get(it.definition.id) }
            if (role == null || !mayKeep(slot, role.definition)) {
                release(t, slot)
                continue
            }
            slot.role = role
            val pos = BlockPos.of(key.pos)
            val valid = t.level.isLoaded(pos) && guarded(role.handler, false) { role.handler.stillValid(t.level, pos) }
            if (!valid) release(t, slot)
        }
    }

    /** After a reload, a worker keeps a claim only while its role still takes it. */
    private fun mayKeep(slot: PastureRuntime.Slot, role: RoleDefinition): Boolean =
        RoleSelection.isEligible(role, typesOf(slot.pokemon)) && (slot.worker.leashed || role.autoAssign)

    /** Steps 3 and 4: the pasture's scan pass, and the assignment when the pass completes. */
    private fun scan(t: Tick, runtime: PastureRuntime) {
        var pass = runtime.scan
        if (pass == null) {
            runtime.syncScanInterval(t.settings.scanIntervalTicks, t.now)
            if (!runtime.scheduler.shouldStart(t.now)) return
            val roles = scanRoles(t, runtime)
            val readsBlocks = roles.any { it.handler.scanKind == ScanKind.BLOCK }
            pass = TargetScan(area(t, runtime), roles, readsBlocks, t.settings.maxBlockChecksPerTick)
            runtime.scheduler.start(t.now)
            runtime.scan = pass
            pass.startPass(t.world)
        }
        if (!pass.stepBlocks(t.world)) return
        runtime.scheduler.complete(t.now)
        runtime.scan = null
        assign(t, runtime, pass.found)
    }

    /** The auto-assign roles that a worker of the pasture can do. Leash-only roles need no scan. */
    private fun scanRoles(t: Tick, runtime: PastureRuntime): List<ActiveRole> {
        val types = runtime.slots.values.filter(::mayAutoAssign).map { typesOf(it.pokemon) }
        if (types.isEmpty()) return emptyList()
        return t.roles.active.filter { role ->
            role.definition.autoAssign &&
                role.handler.scanKind != ScanKind.NONE &&
                types.any { RoleSelection.isEligible(role.definition, it) }
        }
    }

    private fun mayAutoAssign(slot: PastureRuntime.Slot): Boolean =
        !slot.worker.leashed && !slot.worker.tired && !hasFairyType(slot.pokemon) && BrainBridge.canHoldOrder(slot.entity)

    private fun assign(t: Tick, runtime: PastureRuntime, found: List<FoundTarget>) {
        if (found.isEmpty()) return
        val byKey: Map<TargetKey, FoundTarget> = found.associateBy { TargetKey.block(t.dimension, it.pos) }
        val taken = slots.values.filter { it.worker.pasture.dimension == t.dimension }.mapNotNullTo(HashSet()) { it.worker.spot?.asLong() }
        for (slot in runtime.slots.values) {
            val worker = slot.worker
            val current = claims.claimOf(worker.pokemonId)
            val idle = current == null && worker.status == WorkerStatus.IDLE
            val moving = current?.kind == ClaimKind.WORK && t.machine.canMoveFromStandby(worker, t.now)
            if ((!idle && !moving) || !BrainBridge.canHoldOrder(slot.entity)) continue
            val types = typesOf(slot.pokemon)
            val view = WorkerView(worker.pokemonId, slot.entity.position(), types, hasFairyType(slot.pokemon), worker.tired) {
                worker.isSkipped(it, t.now)
            }
            val candidates = byKey.map { (key, target) -> candidate(t, slot, types, key, target) }
            val isFree: (TargetKey) -> Boolean = { claims.isFree(it, worker.pokemonId) }
            val pick = if (current == null) {
                Assigner.pickForIdle(view, candidates, isFree)
            } else {
                Assigner.pickForStandbyMove(view, current.key, candidates, isFree)
            } ?: continue
            val target = byKey.getValue(pick.first.key)
            claim(t, runtime, slot, target, target.roles.first { it.definition.id == pick.second.id }, taken, moving = current != null)
        }
    }

    private fun candidate(t: Tick, slot: PastureRuntime.Slot, types: Set<String>, key: TargetKey, target: FoundTarget): CandidateView {
        val center = target.roles.first().handler.targetCenter(target.pos)
        return CandidateView(key, center, target.roles.map { it.definition }) {
            target.roles.any { role ->
                RoleSelection.isEligible(role.definition, types) &&
                    guarded(role.handler, false) { role.handler.hasDemand(context(t, slot, target.pos, role)) }
            }
        }
    }

    /** A worker gets the claim only with a work spot. With none, it skips the target for `retryWaitTicks`. */
    private fun claim(
        t: Tick,
        runtime: PastureRuntime,
        slot: PastureRuntime.Slot,
        target: FoundTarget,
        role: ActiveRole,
        taken: MutableSet<Long>,
        moving: Boolean,
    ) {
        val worker = slot.worker
        val key = TargetKey.block(t.dimension, target.pos)
        val own = worker.spot?.asLong()
        val grid = LevelSpotGrid(t.level, role.handler, target.pos, if (own == null) taken else taken - own)
        val center = role.handler.targetCenter(target.pos)
        val spot = guarded(role.handler, null) {
            WorkSpots.find(grid, area(t, runtime), center, role.definition.range, slot.entity.position())
        }
        if (spot == null) {
            worker.skipUntil[key] = t.now + t.settings.retryWaitTicks
            return
        }
        if (moving) {
            release(t, slot)
            own?.let(taken::remove)
        }
        if (claims.tryClaim(worker.pokemonId, key, ClaimKind.WORK, runtime.key, t.now) !is ClaimResult.Granted) return
        taken += spot.asLong()
        worker.target = target.pos
        worker.spot = spot
        slot.role = role
        t.machine.onClaim(worker, t.now)
    }

    /** Step 5: arrival, the worker's state, and the handler calls of that state. */
    private fun tickWorker(t: Tick, runtime: PastureRuntime, slot: PastureRuntime.Slot) {
        val worker = slot.worker
        if (slot.entity.isRemoved) {
            drop(t, runtime, slot)
            return
        }
        val claim = claims.claimOf(worker.pokemonId)
        if (claim == null) {
            // Another part of the mod released the claim, so the worker follows.
            if (worker.status != WorkerStatus.IDLE || worker.target != null) release(t, slot)
            return
        }
        if (claim.kind != ClaimKind.WORK) return
        val role = slot.role
        val target = worker.target
        val spot = worker.spot
        if (role == null || target == null || spot == null) {
            release(t, slot)
            return
        }
        val handler = role.handler
        val ctx = WorkContext(t.level, target, slot.entity, slot.pokemon, role.definition, t.now)
        val arrived = isArrived(slot.entity, spot)
        val before = worker.status
        val ok = guarded(handler, false) {
            when (val event = t.machine.onTick(worker, t.now, arrived, { handler.hasDemand(ctx) }, claim.key)) {
                MachineEvent.StartedWorking -> {
                    handler.onStart(ctx)
                    snap(slot.entity, spot, handler.targetCenter(target))
                }

                MachineEvent.EnteredStandby -> when (before) {
                    WorkerStatus.WORKING -> handler.onStop(ctx)
                    WorkerStatus.TRAVELING -> snap(slot.entity, spot, handler.targetCenter(target))
                    else -> {}
                }

                is MachineEvent.ReleaseClaim -> {
                    claims.release(worker.pokemonId)
                    event.skip?.let { worker.skipUntil[it] = event.until }
                    clear(slot)
                }

                else -> {}
            }
            if (worker.status == WorkerStatus.WORKING) handler.applyWork(ctx)
            true
        }
        if (!ok) release(t, slot, callHandler = false)
    }

    /** Step 6: the work order goes into the Brain on every tick, because Cobblemon rebuilds Brains (CD-AI-04). */
    private fun direct(t: Tick, slot: PastureRuntime.Slot) {
        val worker = slot.worker
        val claim = claims.claimOf(worker.pokemonId) ?: return
        val role = slot.role ?: return
        val target = worker.target ?: return
        val spot = worker.spot ?: return
        val entity = slot.entity
        if (claim.kind != ClaimKind.WORK || entity.isRemoved) return
        val lookAt = guarded(role.handler, null) { role.handler.targetCenter(target) }
        BrainBridge.write(entity, WorkOrder(Vec3.atBottomCenterOf(spot), lookAt, hold = isArrived(entity, spot)))
        if (BrainBridge.holdsOrder(entity)) return
        val species = slot.pokemon.species.resourceIdentifier
        if (unbridged.add(species)) {
            CobblemonDynamics.LOGGER.warn("{} has a baseAI that skips Cobblemon's auto behaviours, so it cannot work", species)
        }
        worker.skipUntil[claim.key] = t.now + t.settings.retryWaitTicks
        release(t, slot)
    }

    private fun removePasture(t: Tick, runtime: PastureRuntime, fromIndex: Boolean) {
        for (slot in runtime.slots.values.toList()) drop(t, runtime, slot)
        claims.releaseAll { it.pasture == runtime.key }
        pastures.remove(runtime.key)
        if (fromIndex) {
            val index = t.level.getData(DynamicsAttachments.WORK_PASTURES)
            index.remove(runtime.pos)
            t.level.setData(DynamicsAttachments.WORK_PASTURES, index)
        }
    }

    private fun drop(t: Tick, runtime: PastureRuntime, slot: PastureRuntime.Slot) {
        release(t, slot)
        runtime.slots.remove(slot.worker.pokemonId)
        slots.remove(slot.worker.pokemonId)
    }

    /** The release path: `onStop` when the worker is Working, then Idle with no target, spot or role. */
    private fun release(t: Tick, slot: PastureRuntime.Slot, callHandler: Boolean = true) {
        val worker = slot.worker
        claims.release(worker.pokemonId)
        val role = slot.role
        val target = worker.target
        if (callHandler && worker.status == WorkerStatus.WORKING && role != null && target != null) {
            guarded(role.handler, Unit) { role.handler.onStop(context(t, slot, target, role)) }
        }
        t.machine.onRelease(worker, t.now)
        clear(slot)
    }

    private fun clear(slot: PastureRuntime.Slot) {
        slot.worker.target = null
        slot.worker.spot = null
        slot.role = null
        BrainBridge.clear(slot.entity)
    }

    private fun context(t: Tick, slot: PastureRuntime.Slot, target: BlockPos, role: ActiveRole) =
        WorkContext(t.level, target, slot.entity, slot.pokemon, role.definition, t.now)

    private fun area(t: Tick, runtime: PastureRuntime): WorkArea = WorkArea.of(runtime.pos, t.settings, PastureAdapter.maxWanderDistance())

    companion object {
        @Volatile
        private var instance: WorkEngine? = null

        fun start(server: MinecraftServer) {
            instance = WorkEngine(server)
        }

        fun stop() {
            instance = null
        }

        fun get(): WorkEngine? = instance

        fun require(server: MinecraftServer): WorkEngine =
            instance?.takeIf { it.server === server } ?: error("The work engine is not running")

        /** At most `max(0.75, bbWidth / 2)` from the spot center horizontally, and less than 1.5 blocks from its floor. */
        fun isArrived(entity: Entity, spot: BlockPos): Boolean {
            val dx = entity.x - (spot.x + 0.5)
            val dz = entity.z - (spot.z + 0.5)
            val reach = max(0.75, entity.bbWidth / 2.0)
            return dx * dx + dz * dz <= reach * reach && abs(entity.y - spot.y) < 1.5
        }

        private fun typesOf(pokemon: Pokemon): Set<String> = pokemon.types.mapTo(HashSet()) { it.showdownId }

        private fun hasFairyType(pokemon: Pokemon): Boolean = pokemon.types.any { it == ElementalTypes.FAIRY }

        /** On arrival: the spot center, at rest, facing the target. The Brain's work activity holds it from the next tick. */
        private fun snap(entity: PokemonEntity, spot: BlockPos, lookAt: Vec3) {
            entity.navigation.stop()
            entity.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
            entity.teleportTo(spot.x + 0.5, spot.y.toDouble(), spot.z + 0.5)
            entity.deltaMovement = Vec3.ZERO
            BrainBridge.face(entity, lookAt)
        }
    }
}

/** A handler that throws is turned off with one warning (CD-PERF-05). The next tick releases the claims of its roles. */
private inline fun <T> guarded(handler: TargetHandler, fallback: T, block: () -> T): T = try {
    block()
} catch (e: Exception) {
    RoleRegistry.disableHandler(handler.id, e.toString())
    fallback
}

/** The loaded part of a level. A scan never loads a chunk. */
private class LevelScanWorld(private val level: ServerLevel) : ScanWorld {
    private val cursor = BlockPos.MutableBlockPos()

    override fun blockEntitiesIn(area: WorkArea): Sequence<Pair<BlockPos, BlockEntity>> = sequence {
        val (min, max) = area.chunkRange()
        for (cx in min.x..max.x) {
            for (cz in min.z..max.z) {
                val chunk = level.chunkSource.getChunkNow(cx, cz) ?: continue
                for ((pos, blockEntity) in chunk.blockEntities.entries.toList()) if (area.contains(pos)) yield(pos to blockEntity)
            }
        }
    }

    override fun blockState(x: Int, y: Int, z: Int): BlockState {
        val chunk = level.chunkSource.getChunkNow(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))
            ?: return Blocks.AIR.defaultBlockState()
        return chunk.getBlockState(cursor.set(x, y, z))
    }

    override fun match(handler: TargetHandler, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): BlockPos? =
        guarded(handler, null) { if (handler.matches(level, pos, state, blockEntity)) handler.canonical(level, pos) else null }
}
