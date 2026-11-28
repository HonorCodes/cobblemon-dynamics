package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.Feedback
import codes.honor.cobblemondynamics.OnceLogger
import codes.honor.cobblemondynamics.ai.BrainBridge
import codes.honor.cobblemondynamics.ai.WorkOrder
import codes.honor.cobblemondynamics.bed.BedBlock
import codes.honor.cobblemondynamics.bed.BedBlockEntity
import codes.honor.cobblemondynamics.bed.BedCandidate
import codes.honor.cobblemondynamics.bed.BedSelector
import codes.honor.cobblemondynamics.claim.Claim
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.claim.ClaimRegistry
import codes.honor.cobblemondynamics.claim.ClaimResult
import codes.honor.cobblemondynamics.claim.TargetKey
import codes.honor.cobblemondynamics.compat.LandClaims
import codes.honor.cobblemondynamics.compat.WorkerEvents
import codes.honor.cobblemondynamics.config.EngineSettings
import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.fx.EffectPlayer
import codes.honor.cobblemondynamics.handler.BindingShape
import codes.honor.cobblemondynamics.handler.ClaimMemo
import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.handler.sentry.SentryHandler
import codes.honor.cobblemondynamics.handler.sentry.SentryWatch
import codes.honor.cobblemondynamics.handler.tree.LumberjackHandler
import codes.honor.cobblemondynamics.handler.tree.TreeFactsReader
import codes.honor.cobblemondynamics.heal.AllyView
import codes.honor.cobblemondynamics.heal.HealTargets
import codes.honor.cobblemondynamics.heal.HealTransaction
import codes.honor.cobblemondynamics.leash.Binding
import codes.honor.cobblemondynamics.leash.BindingStore
import codes.honor.cobblemondynamics.leash.LeashResets
import codes.honor.cobblemondynamics.pasture.PastureAdapter
import codes.honor.cobblemondynamics.pasture.PastureKey
import codes.honor.cobblemondynamics.pasture.PastureOwnerRules
import codes.honor.cobblemondynamics.pasture.TetheredPokemon
import codes.honor.cobblemondynamics.registry.DynamicsAttachments
import codes.honor.cobblemondynamics.role.ActiveRole
import codes.honor.cobblemondynamics.role.RoleDefinition
import codes.honor.cobblemondynamics.role.RoleRegistry
import codes.honor.cobblemondynamics.role.RoleSelection
import codes.honor.cobblemondynamics.role.RoleSet
import codes.honor.cobblemondynamics.stamina.EvaluatedTraits
import codes.honor.cobblemondynamics.stamina.FlushRule
import codes.honor.cobblemondynamics.stamina.MateCandidate
import codes.honor.cobblemondynamics.stamina.RecoveryRule
import codes.honor.cobblemondynamics.stamina.SlackMath
import codes.honor.cobblemondynamics.stamina.StaminaMath
import codes.honor.cobblemondynamics.stamina.StaminaStore
import codes.honor.cobblemondynamics.stamina.StaminaView
import codes.honor.cobblemondynamics.stamina.ViewSync
import codes.honor.cobblemondynamics.stamina.WorkProgression
import codes.honor.cobblemondynamics.stamina.WorkTraits
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.SectionPos
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.BlockTags
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LeavesBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.pathfinder.PathType
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * The server's work engine: the workers of every pasture in Work Mode and of every Quick Work order, their claims and their
 * states. Runs on the server thread only, and holds nothing that a restart keeps (CD-CLM-07).
 */
class WorkEngine private constructor(val server: MinecraftServer) {
    /**
     * A block target that a binding of an exclusive role reserves belongs to its bound worker, read from the target level's index
     * (CD-CLM-06). A target takes up to `logistics.maxWorkersPerContainer` shared claims of logistics roles (CD-CLM-10).
     */
    val claims = ClaimRegistry({ WorkEngineSettings.current.maxWorkersPerContainer }) { key -> reservedFor(key) }

    private val pastures = LinkedHashMap<PastureKey, PastureRuntime>()
    private val slots = HashMap<UUID, WorkerSlot>()

    /** Quick Work orders by Pokémon UUID (ADR-0013). Empty most of the time, and then no Quick Work code runs per tick. */
    private val orders = LinkedHashMap<UUID, QuickRuntime>()

    /** The aim of each send-out by Pokémon UUID, from the key press until Cobblemon ends the send-out (CD-QWK-01). */
    private val aims = HashMap<UUID, QuickAim>()

    /** One warning for each species whose Brain cannot hold a work order. */
    private val unbridged = OnceLogger()
    private val unfreed = OnceLogger()
    private val unstepped = OnceLogger()

    /** CD-ASG-26: the places where a tree grew since the last tick of their level, at most [MAX_GROWTHS]. */
    private val growths = ArrayList<Pair<ServerLevel, BlockPos>>()

    fun worker(pokemonId: UUID): Worker? = slots[pokemonId]?.worker

    fun entityOf(pokemonId: UUID): PokemonEntity? = slots[pokemonId]?.entity?.takeUnless { it.isRemoved }

    fun pokemonOf(pokemonId: UUID): Pokemon? = slots[pokemonId]?.pokemon

    /** The recovery rule that the engine applies to a worker, from its types and its binding at the last poll. */
    fun ruleOf(pokemonId: UUID): RecoveryRule? = slots[pokemonId]?.rule

    /** The role of a worker's work claim, while it holds one. */
    fun roleOf(pokemonId: UUID): ActiveRole? = slots[pokemonId]?.role

    fun traitsOf(pokemonId: UUID): Int = slots[pokemonId]?.traits?.flags ?: 0

    fun multiplierOf(pokemonId: UUID): Double = slots[pokemonId]?.traits?.combinedMultiplier ?: 1.0

    /**
     * True while [entity] is the entity of a worker that the engine directs: any state but Idle, in which the worker walks to or holds
     * a place that the engine gave it. Two such workers do not push each other (`WorkerPush`).
     */
    fun isDirected(entity: PokemonEntity): Boolean {
        val slot = slots[entity.pokemon.uuid] ?: return false
        return slot.entity === entity && slot.worker.status != WorkerStatus.IDLE
    }

    fun isQuickWorker(pokemonId: UUID): Boolean = pokemonId in orders

    fun quickWorkOwnerOf(pokemonId: UUID): UUID? = orders[pokemonId]?.owner

    fun aimOf(pokemonId: UUID): QuickAim? = aims[pokemonId]

    /**
     * CD-QWK-01: a send-out replaces the Pokémon's aim, and one with no block in view ([aim] null) leaves none. A new aim drops every
     * aim that can no longer start an order (CD-QWK-25).
     */
    fun keepAim(pokemonId: UUID, aim: QuickAim?) {
        if (aim == null) {
            aims.remove(pokemonId)
            return
        }
        QuickWorkRules.dropExpired(aims, aim.tick)
        aims[pokemonId] = aim
    }

    /** CD-QWK-25: a player who logs out leaves no aim. Its orders end on the next tick (CD-QWK-20). */
    fun forgetAims(owner: UUID) {
        aims.values.removeIf { it.owner == owner }
    }

    /** One engine tick of [level], after the level ticked its block entities and entities. */
    fun tickLevel(level: ServerLevel) {
        val dimension = level.dimension().location()
        if (growths.isNotEmpty()) drainGrowths(level)
        val indexed = level.getExistingDataOrNull(DynamicsAttachments.WORK_PASTURES)?.all().orEmpty()
        // CD-QWK-24: with no order, this check is all the Quick Work that a level tick does.
        val quick = if (orders.isEmpty()) emptyList() else orders.values.filter { it.level === level }
        if (indexed.isEmpty() && quick.isEmpty() && pastures.keys.none { it.dimension == dimension }) return
        val t = tick(level)
        for (pos in indexed) {
            val key = PastureKey(dimension, pos)
            pastures.getOrPut(key) { PastureRuntime(level, key, t.now, t.settings.scanIntervalTicks) }
        }
        for (runtime in runtimesOf(dimension)) if (runtime.key.pos !in indexed) removePasture(t, runtime, fromIndex = false)

        for (runtime in runtimesOf(dimension)) if (level.isLoaded(runtime.pos) && runtime.isPollDue(t.now)) poll(t, runtime)
        validateClaims(t)
        for (runtime in runtimesOf(dimension)) if (level.isLoaded(runtime.pos)) scan(t, runtime)
        for (runtime in runtimesOf(dimension)) {
            for (slot in runtime.slots.values.toList()) {
                tickWorker(t, runtime, slot)
                if (runtime.slots[slot.worker.pokemonId] === slot) tickStamina(t, runtime, slot)
            }
        }
        for (order in quick) tickOrder(t, order)
        for (runtime in runtimesOf(dimension)) for (slot in runtime.slots.values.toList()) direct(t, runtime, slot)
        for (order in quick) if (orders[order.slot.worker.pokemonId] === order) direct(t, order, order.slot)
    }

    /** CD-ASG-26: a tree grows at [pos]. The next tick of [level] frees the workers that it filled. A full queue drops the place. */
    fun queueGrowth(level: ServerLevel, pos: BlockPos) {
        if (growths.size < MAX_GROWTHS) growths += level to pos.immutable()
    }

    /** CD-ASG-26: frees the workers near each growth of [level]. A throw from another mod's block skips the rest and logs once. */
    private fun drainGrowths(level: ServerLevel) = safely {
        val here = growths.filter { it.first === level }
        if (here.isEmpty()) return@safely
        growths.removeAll { it.first === level }
        val t = tick(level)
        for ((_, pos) in here) freeAround(t, pos)
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
        val site = siteOf(slot) ?: return
        release(tick(site.level), slot)
    }

    /**
     * A new binding of an exclusive role takes its target from any other worker in this tick (CD-CLM-05). A binding of a logistics role
     * takes nothing, because its claim is shared (CD-CLM-11). The bound worker's pasture polls on the next engine tick, which ends the
     * worker's claim elsewhere and claims the bound target.
     */
    fun onBound(level: ServerLevel, pokemonId: UUID, binding: Binding) {
        val t = tick(level)
        val shared = RoleRegistry.sharesTargets(binding.roleId)
        val key = TargetKey.block(t.dimension, binding.target, RoleRegistry.laneOf(binding.roleId))
        val holder = if (shared) null else claims.holderOf(key)
        if (holder != null && holder.worker != pokemonId) {
            val slot = slots[holder.worker]
            if (slot != null) release(t, slot) else claims.release(holder.worker)
        }
        pastures[PastureKey(t.dimension, binding.pasture.asLong())]?.requestPoll()
    }

    /** The worker is no longer leashed. A claim on its old target stays only while its role may auto-assign. */
    fun onUnbound(pokemonId: UUID) {
        val slot = slots[pokemonId] ?: return
        slot.binding = null
        slot.worker.leashed = false
    }

    /** CD-QWK-02 to CD-QWK-11: Cobblemon ended a send-out. Its aim, if it has one, becomes an order, a refusal or nothing. */
    fun onSentOut(level: ServerLevel, pokemon: Pokemon, entity: PokemonEntity) {
        val aim = aims.remove(pokemon.uuid) ?: return
        val t = tick(level)
        if (!t.settings.quickWorkEnabled || QuickWorkRules.isExpired(aim.tick, t.now) || aim.dimension != t.dimension) return
        if (entity.isRemoved || entity.isBattling || pokemon.uuid in slots || !BrainBridge.canHoldOrder(entity)) return
        // CD-QWK-25: only the party Pokémon of the player who aimed, as at the key press.
        val store = pokemon.storeCoordinates.get()?.store as? PlayerPartyStore
        if (pokemon.isFainted() || pokemon.isBattleClone() || store?.playerUUID != aim.owner) return
        val owner = server.playerList.getPlayer(aim.owner)?.takeIf { it.level() === level } ?: return
        // CD-QWK-21: an owner who entered a battle while the Pokémon came out gets no order.
        if (BattleRegistry.getBattleByParticipatingPlayer(owner) != null || !level.isLoaded(aim.pos)) return
        val types = typesOf(pokemon)
        val state = level.getBlockState(aim.pos)
        val blockEntity = level.getBlockEntity(aim.pos)
        val fitting = t.roles.forTypes(types).filter { role ->
            guarded(role.handler, false) { role.handler.matches(level, aim.pos, state, blockEntity) }
        }
        val byId = fitting.associateBy { it.definition.id }
        // CD-CRP-21: a slot that no engine map holds, so that a handler can weigh the aimed block for this Pokémon.
        val probe = WorkerSlot(Worker(pokemon.uuid, pasture = null), entity, pokemon)
        val pick = QuickWorkRules.pick(
            fitting.map { it.definition },
            types.toList(),
            hasFairyType(pokemon),
            orderable = { def ->
                val handler = byId.getValue(def.id).handler
                QuickWorkRules.mayOrder(def, handler.scanKind != ScanKind.NONE, handler.bindingShape)
            },
            hasDemand = { def ->
                val role = byId.getValue(def.id)
                guarded(role.handler, false) { role.handler.hasDemand(context(t, probe, role.handler.canonical(level, aim.pos), role)) }
            },
            sameGroup = RoleRegistry::sameGroup,
        )
        val name = state.block.name
        when (pick) {
            QuickWorkRules.Pick.None -> {}
            QuickWorkRules.Pick.NeedsLeash -> tell(aim.owner, QuickWorkRules.NEEDS_LEASH, pokemon, name)
            is QuickWorkRules.Pick.Order -> start(t, owner, pokemon, entity, aim, byId.getValue(pick.role.id), name)
        }
    }

    /**
     * CD-FAN-04: whether [pokemonId]'s worker has a spot at [canonical] for [role]. The engine knows a newly pastured Pokémon only from
     * its pasture's next poll, so until then the check finds the entity among the pasture's tethered entities. True only when no entity
     * exists for it, so the claim decides.
     */
    fun hasSpot(level: ServerLevel, pasture: BlockPos, pokemonId: UUID, role: ActiveRole, canonical: BlockPos): Boolean {
        val entity = entityOf(pokemonId) ?: PastureAdapter.tetheredEntity(level, pasture, pokemonId) ?: return true
        val t = tick(level)
        val others = takenSpots(t).also { taken -> slots[pokemonId]?.worker?.spot?.let { taken.remove(it.asLong()) } }
        val area = WorkArea.of(pasture, t.settings, PastureAdapter.maxWanderDistance())
        // At bind time only room counts: a missing path shows later as the "cannot reach" message (CD-LSH-21).
        return guarded(role.handler, true) { spotFor(level, area, entity, role, canonical, others) { true } != null }
    }

    /**
     * Every worker's live Work Stamina goes to its Pokémon's saved data. The server stop calls it before Cobblemon saves its stores
     * (ADR-0008).
     */
    fun flushAll() {
        val now = server.overworld().gameTime
        for (slot in slots.values) flush(slot, now)
    }

    /**
     * Ends the work of every Working worker through its handler, as a release does, so the saved world keeps no fuel, heat or drive
     * that a worker gave (CD-BRF-05). The server stop calls it before the levels save. Claims are never saved, so the slots stay as
     * they are.
     */
    fun stopWorkingHandlers() {
        for (slot in slots.values) {
            val worker = slot.worker
            val role = slot.role ?: continue
            val target = worker.target ?: continue
            val level = slot.entity.level() as? ServerLevel ?: continue
            if (worker.status != WorkerStatus.WORKING) continue
            val t = tick(level)
            guarded(role.handler, Unit) { role.handler.onStop(context(t, slot, target, role, slot.memo)) }
        }
    }

    /**
     * The GameTest form of a restart. A real stop flushes Work Stamina first. A restart also loses the Brain memories, because the
     * work order has no codec, and the focus entities, because their type is never saved.
     */
    fun resetRuntimeForTest() {
        flushAll()
        for (slot in slots.values) {
            // A real restart loses what a handler holds for a claim, such as an unsaved attachment, with the entity.
            siteOf(slot)?.let { unclaim(tick(it.level), slot, slot.entity) }
            BrainBridge.clear(slot.entity)
            removeFocus(slot)
        }
        claims.clear()
        pastures.clear()
        slots.clear()
        orders.clear()
        aims.clear()
        SentryWatch.reset()
    }

    private class Tick(val level: ServerLevel, val settings: EngineSettings, val roles: RoleSet) {
        val now: Long = level.gameTime
        val dimension: ResourceLocation = level.dimension().location()
        val machine = WorkerMachine(settings)
        val world: ScanWorld = LevelScanWorld(level)
    }

    private fun tick(level: ServerLevel) = Tick(level, WorkEngineSettings.current, RoleRegistry.current)

    private fun runtimesOf(dimension: ResourceLocation): List<PastureRuntime> = pastures.values.filter { it.key.dimension == dimension }

    private fun siteOf(slot: WorkerSlot): WorkSite? = slot.worker.pasture?.let { pastures[it] } ?: orders[slot.worker.pokemonId]

    private fun dimensionOf(slot: WorkerSlot): ResourceLocation? =
        slot.worker.pasture?.dimension ?: orders[slot.worker.pokemonId]?.dimension

    /** Step 1: workers join and leave with the pasture's tethered list (CD-ASG-01, CD-ASG-15). A pasture that is gone leaves. */
    private fun poll(t: Tick, runtime: PastureRuntime) {
        runtime.markPolled()
        val be = PastureAdapter.pastureAt(t.level, runtime.pos)
        if (be == null || !PastureAdapter.isWorkMode(be)) {
            removePasture(t, runtime, fromIndex = true)
            return
        }
        // CD-LND-05: every pasture check of this poll and of the next scan pass asks about the placer that this poll reads.
        runtime.placer = PastureAdapter.placer(be)
        val present = HashSet<UUID>()
        for (tethered in PastureAdapter.tethered(be)) {
            val pokemon = tethered.pokemon ?: continue
            val entity = tethered.entity ?: continue
            if (!canWork(t.level, tethered, pokemon, entity)) continue
            present += tethered.pokemonId
            val resolvedOwner = PastureOwnerRules.ownerId(tethered.ownerId, tethered.pcId, pokemon.getOwnerUUID())
            val slot = runtime.slots[tethered.pokemonId]
            if (slot != null) {
                val old = slot.entity
                slot.entity = entity
                slot.pokemon = pokemon
                slot.ownerId = resolvedOwner
                if (old !== entity) moveClaim(t, slot, old)
            } else if (tethered.pokemonId !in slots) {
                val worker = Worker(tethered.pokemonId, runtime.key)
                worker.stamina = StaminaStore.load(pokemon, t.now, t.settings)
                worker.staminaUntil = t.now
                val joined = WorkerSlot(worker, entity, pokemon)
                joined.ownerId = resolvedOwner
                runtime.slots[tethered.pokemonId] = joined
                slots[tethered.pokemonId] = joined
            }
        }
        for (slot in runtime.slots.values.toList()) if (slot.worker.pokemonId !in present) drop(t, runtime, slot)
        val mated = WorkTraits.matedWorkers(
            runtime.slots.values.map { slot ->
                MateCandidate(slot.worker.pokemonId, slot.pokemon.species.resourceIdentifier, slot.pokemon.gender)
            },
        )
        val taken = takenSpots(t)
        for (slot in runtime.slots.values.toList()) {
            slot.hasMate = slot.worker.pokemonId in mated
            slot.binding = BindingStore.read(slot.pokemon)?.takeIf { it.dimension == t.dimension && it.pasture == runtime.pos }
            slot.binding?.let { binding ->
                landRefusal(t, runtime, slot, binding)?.let { reason ->
                    LeashResets.clear(server, slot.pokemon, reason)
                    slot.binding = null
                }
            }
            claims.claimOf(slot.worker.pokemonId)?.let { claim ->
                val mayUseClaim = placerMayUse(t, runtime, slot, claim.key)
                val mayBreakClaim = mayUseClaim && claimMayBreak(t, runtime, slot, claim)
                if (!mayUseClaim || !mayBreakClaim) {
                    when (claim.kind) {
                        ClaimKind.WORK -> {
                            if (!mayBreakClaim && mayUseClaim) {
                                slot.worker.skipUntil[claim.key] = t.now + t.settings.retryWaitTicks
                            }
                            release(t, slot)
                        }

                        ClaimKind.BED -> claims.release(slot.worker.pokemonId)

                        ClaimKind.HEAL -> {}
                    }
                }
            }
            slot.worker.leashed = slot.binding != null
            slot.rule = RecoveryRule.of(hasFairyType(slot.pokemon), slot.binding != null)
            slot.probes.follow(slot.binding?.let { it.target to it.roleId } ?: slot.worker.target)
            claimBound(t, runtime, slot, taken)
            switchInStandby(t, runtime, slot, taken)
            refreshTraits(t, slot)
            WorkerEvents.polled(slot.pokemon)
        }
    }

    private fun actorsOf(runtime: PastureRuntime, slot: WorkerSlot, binding: Binding? = slot.binding): List<UUID?> =
        listOf(runtime.placer, slot.ownerId, binding?.binder)

    private fun landRefusal(t: Tick, runtime: PastureRuntime, slot: WorkerSlot, binding: Binding): String? {
        val actors = actorsOf(runtime, slot, binding)
        return when {
            !LandClaims.actorsMayUse(actors, t.level, binding.target) -> LeashResets.LAND_CLAIM
            binding.destination?.let { !LandClaims.actorsMayUse(actors, t.level, it) } == true -> LeashResets.LAND_CLAIM_DESTINATION
            else -> null
        }
    }

    private fun placerMayUse(t: Tick, runtime: PastureRuntime, slot: WorkerSlot, key: TargetKey): Boolean {
        val block = key as? TargetKey.Block ?: return true
        return block.dimension != t.dimension || LandClaims.actorsMayUse(actorsOf(runtime, slot), t.level, BlockPos.of(block.pos))
    }

    private fun claimMayBreak(t: Tick, runtime: PastureRuntime, slot: WorkerSlot, claim: Claim): Boolean {
        if (claim.kind != ClaimKind.WORK) return true
        val block = claim.key as? TargetKey.Block ?: return true
        if (block.dimension != t.dimension) return true
        val role = slot.role ?: return true
        if (!role.handler.breaksBlocks) return true
        if (role.handler === LumberjackHandler && LumberjackHandler.hasActiveRun(slot.memo)) return true
        val pos = BlockPos.of(block.pos)
        if (!t.level.isLoaded(pos)) return true
        val cells = guarded(role.handler, emptyList()) { role.handler.breakTargets(t.level, pos) }
        if (cells.isEmpty()) return true
        val actors = actorsOf(runtime, slot)
        return BreakingClaimGate.mayClaimCells(mayUse = true, cells) { cell -> LandClaims.actorsMayBreak(actors, t.level, cell) }
    }

    /**
     * Cobblemon gave the worker a new entity, which [slot] holds now. Its work claim, if it holds one, leaves [old] and follows the
     * Pokémon, because [old] can outlive the change.
     */
    private fun moveClaim(t: Tick, slot: WorkerSlot, old: PokemonEntity) {
        BrainBridge.clear(old)
        unclaim(t, slot, old)
        val role = slot.role ?: return
        val target = slot.worker.target ?: return
        guarded(role.handler, Unit) { role.handler.onClaim(context(t, slot, target, role, slot.memo)) }
    }

    /**
     * A leashed worker claims its bound target and no other (CD-ASG-20). A Tired worker waits until it has rested (CD-STA-14). While its
     * crop's cell is empty, the worker holds no claim (CD-CLM-08) and so no work order, and it waits where it stands (CD-CRP-09). A work
     * claim on the bound target in the lane of a switch role stays, and a worker with no claim takes the first switch role with demand
     * there, or else its bound role (CD-LSH-30).
     */
    private fun claimBound(t: Tick, runtime: PastureRuntime, slot: WorkerSlot, taken: MutableSet<Long>) {
        val binding = slot.binding ?: return
        val worker = slot.worker
        val key = TargetKey.block(t.dimension, binding.target, RoleRegistry.laneOf(binding.roleId))
        val current = claims.claimOf(worker.pokemonId)
        if (current?.kind == ClaimKind.WORK && current.key != key && !isSwitchedClaim(t, slot, binding)) release(t, slot)
        if (claims.claimOf(worker.pokemonId) != null || worker.tired || worker.isSkipped(key, t.now)) return
        if (!BrainBridge.canHoldOrder(slot.entity)) return
        // CD-PCK-04: a role that no longer fits, or that a data pack set to leash false, waits for LeashResets to clear the binding.
        val role = t.roles.get(binding.roleId)?.takeIf { RoleSelection.bindingFits(it.definition, typesOf(slot.pokemon)) } ?: return
        if (!t.level.isLoaded(binding.target)) return
        val switched = switchFor(t, slot, binding.target, role) {
            guarded(role.handler, false) {
                role.handler.hasDemand(context(t, slot, binding.target, role, slot.probes.memo(role.definition.id)))
            }
        }
        if (switched != null && claimSwitch(t, runtime, slot, binding.target, switched, taken, moving = false)) return
        if (guarded(role.handler, false) { role.handler.stillValid(t.level, binding.target) }) {
            val attempt = claim(t, runtime, slot, binding.target, role, taken, moving = false)
            if (attempt == Attempt.NO_SPOT) cannotReach(t, slot, binding.target)
        } else if (guarded(role.handler, false) { role.handler.bindingHolds(t.level, binding.target) }) {
            // Each poll renews the pause before it lapses. Until the first poll, the hold of the released order pauses the wander.
            BrainBridge.pauseWander(slot.entity, WAIT_PAUSE_TICKS)
        }
    }

    /**
     * CD-LSH-33: the worker's work claim names its bound target with a role that may switch in there: of the worker's types, of another
     * handler than the bound role's, and whose binding would name one target and whose claims share no target. The handler checks of the
     * cell ran when the switch took the claim, and `validateClaims` ends a claim whose target is no longer valid.
     */
    private fun isSwitchedClaim(t: Tick, slot: WorkerSlot, binding: Binding): Boolean {
        if (slot.worker.target != binding.target) return false
        val role = slot.role ?: return false
        val own = t.roles.get(binding.roleId) ?: return false
        if (!RoleSelection.isEligible(role.definition, typesOf(slot.pokemon))) return false
        val single = { def: RoleDefinition -> isSingle(if (def.id == own.definition.id) own else role) }
        return RoleSwitch.rolesOf(own.definition, listOf(role.definition), bound = true, single, { true }, { true }).size > 1
    }

    /**
     * CD-ASG-21, CD-LSH-30: a worker in Standby on its target, with no missed arrival, whose role has no demand there, takes the first
     * switch role with demand on the same target in this tick. A Quick Worker has no poll, so it keeps its role (decision 13). The switch
     * roles of a bound worker are those of its bound role, also while it works in a switch role (CD-LSH-33).
     */
    private fun switchInStandby(t: Tick, runtime: PastureRuntime, slot: WorkerSlot, taken: MutableSet<Long>) {
        val worker = slot.worker
        if (worker.status != WorkerStatus.STANDBY || worker.missedArrival) return
        val role = slot.role ?: return
        val target = worker.target ?: return
        if (!RoleSwitch.maySwitch(slot.binding != null, hasFairyType(slot.pokemon)) || !t.level.isLoaded(target)) return
        if (claims.claimOf(worker.pokemonId)?.kind != ClaimKind.WORK || !BrainBridge.canHoldOrder(slot.entity)) return
        val binding = slot.binding
        val own = if (binding == null) role else t.roles.get(binding.roleId) ?: return
        val next = switchFor(t, slot, target, own, current = role) {
            guarded(role.handler, false) { role.handler.hasDemand(context(t, slot, target, role, slot.memo)) }
        } ?: return
        claimSwitch(t, runtime, slot, target, next, taken, moving = true)
    }

    /**
     * CD-LSH-30, CD-LSH-32, CD-ASG-21: the switch role that the worker of [slot] takes on [target] in place of [current], or null to keep
     * [current]. [own] is the bound role, or the claimed role of an auto worker, whose switch roles the worker may take. [currentDemand]
     * asks [current]'s demand, and each other switch role asks through its memo of the slot (CD-LSH-34). A worker with no switch role
     * asks no demand, so a worker of one type costs only the cheap checks of `RoleSwitch.rolesOf`. Unknown switch roles keep [current]
     * (CD-LSH-35).
     */
    private fun switchFor(
        t: Tick,
        slot: WorkerSlot,
        target: BlockPos,
        own: ActiveRole,
        current: ActiveRole = own,
        currentDemand: () -> Boolean,
    ): ActiveRole? {
        val types = typesOf(slot.pokemon)
        val bound = slot.binding != null
        val roles = switchRolesOn(t.level, target, own, t.roles, types, bound, cache = slot.switchRoles, now = t.now) ?: return null
        if (roles.size < 2) return null
        val byId = roles.associateBy { it.definition.id } + (current.definition.id to current)
        val me = slot.worker.pokemonId
        val keyOf = { def: RoleDefinition -> TargetKey.block(t.dimension, target, byId.getValue(def.id).handler.claimLane) }
        val demandOf = { def: RoleDefinition ->
            val role = byId.getValue(def.id)
            guarded(role.handler, false) { role.handler.hasDemand(context(t, slot, target, role, slot.probes.memo(def.id))) }
        }
        val pick = RoleSwitch.choose(
            current.definition,
            roles.map { it.definition },
            types.toList(),
            hasDemand = { def -> if (def.id == current.definition.id) currentDemand() else demandOf(def) },
            // CD-LSH-32: a free lane, or one that this worker holds itself, as the two roles of a brewing stand share theirs.
            laneFree = { def -> keyOf(def).let { claims.isFree(it, me) || claims.holderOf(it)?.worker == me } },
            skipped = { def -> slot.worker.isSkipped(keyOf(def), t.now) },
            sameGroup = RoleRegistry::sameGroup,
        )
        return pick?.takeIf { it.id != current.definition.id }?.let { byId.getValue(it.id) }
    }

    /**
     * Claims [target] with the switch role [role]. With [moving], the worker keeps its claim until a spot is found. A claim that fails
     * skips that role's key for `retryWaitTicks`, so the next poll tries the bound role again. A worker whose navigation cannot plan yet,
     * as in the air, skips nothing and tries again at its next poll, as `claim` does.
     */
    private fun claimSwitch(
        t: Tick,
        runtime: PastureRuntime,
        slot: WorkerSlot,
        target: BlockPos,
        role: ActiveRole,
        taken: MutableSet<Long>,
        moving: Boolean,
    ): Boolean {
        val attempt = claim(t, runtime, slot, target, role, taken, moving)
        if (attempt == Attempt.GRANTED) return true
        if (attempt == Attempt.NOT_NOW) return false
        slot.worker.skipUntil[TargetKey.block(t.dimension, target, role.handler.claimLane)] = t.now + t.settings.retryWaitTicks
        return false
    }

    /** The work spots that workers of this level hold, as `BlockPos.asLong()`, Quick Workers' included. */
    private fun takenSpots(t: Tick): MutableSet<Long> =
        slots.values.filter { dimensionOf(it) == t.dimension }.mapNotNullTo(HashSet()) { it.worker.spot?.asLong() }

    /** CD-LSH-21: the binder or owner of a leashed worker learns why its machine waits. */
    private fun cannotReach(t: Tick, slot: WorkerSlot, target: BlockPos) {
        val owner = slot.binding?.binder ?: slot.ownerId ?: slot.pokemon.getOwnerUUID() ?: return
        val name = t.level.getBlockState(target).block.name
        Feedback.actionBar(owner, server, Component.translatable(CANNOT_REACH, slot.pokemon.getDisplayName(), name))
    }

    /** Owned, not fainted, no battle clone, in this level, and tethered by its owner (CD-TEAM-04). */
    private fun canWork(level: ServerLevel, tethered: TetheredPokemon, pokemon: Pokemon, entity: PokemonEntity): Boolean =
        PastureOwnerRules.canWork(
            tetheredOwnerId = tethered.ownerId,
            tetheredPcId = tethered.pcId,
            liveOwnerId = pokemon.getOwnerUUID(),
            fainted = pokemon.isFainted(),
            battleClone = pokemon.isBattleClone(),
            removed = entity.isRemoved,
            sameLevel = entity.level() === level,
        )

    /**
     * Step 2: a claim of any kind ends when no worker holds it. A work claim also ends when its target is gone or invalid
     * (CD-CLM-08) or its role is gone. Each resting worker checks its own bed (CD-BED-11).
     */
    private fun validateClaims(t: Tick) {
        for (claim in claims.all()) {
            val slot = slots[claim.worker]
            if (slot == null) {
                claims.release(claim.worker)
                continue
            }
            val key = claim.key as? TargetKey.Block ?: continue
            if (claim.kind != ClaimKind.WORK || key.dimension != t.dimension) continue
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

    /**
     * After a reload, a worker keeps a claim only while its role still takes it: through its binding, an auto claim, or the Quick Work
     * order of a role that opts in (CD-QWK-36). A Quick Worker has no pasture.
     */
    private fun mayKeep(slot: WorkerSlot, role: RoleDefinition): Boolean {
        val allowedTypes = if (!slot.worker.leashed && slot.worker.pasture != null && hasFairyType(slot.pokemon)) {
            typesOf(slot.pokemon).filterTo(LinkedHashSet()) { it != DualFairyRule.FAIRY_TYPE }
        } else {
            typesOf(slot.pokemon)
        }
        return RoleSelection.isEligible(role, allowedTypes) &&
            (slot.worker.leashed || role.autoAssign || (slot.worker.pasture == null && role.quickWork == true))
    }

    /** Steps 3 and 4: the pasture's scan pass, and the assignment when the pass completes. */
    private fun scan(t: Tick, runtime: PastureRuntime) {
        var pass = runtime.scan
        if (pass == null) {
            runtime.syncScanInterval(t.settings.scanIntervalTicks, t.now)
            if (!runtime.scheduler.shouldStart(t.now)) return
            val roles = scanRoles(t, runtime)
            val readsBlocks = roles.any { it.handler.scanKind == ScanKind.BLOCK }
            pass = TargetScan(runtime.area(t.settings), roles, readsBlocks, t.settings.maxBlockChecksPerTick)
            runtime.scheduler.start(t.now)
            runtime.scan = pass
            pass.startPass(t.world)
        }
        if (!pass.stepBlocks(t.world)) return
        runtime.scheduler.complete(t.now)
        runtime.scan = null
        assignHeals(t, runtime)
        assign(t, runtime, pass.found)
    }

    /**
     * After each scan pass, every eligible healer of the pasture picks an ally of the same pasture and takes a heal claim on it
     * (CD-HEAL-01, CD-TEAM-10). An unbound dual-type Fairy worker holding an auto work claim releases it first when an ally qualifies.
     */
    private fun assignHeals(t: Tick, runtime: PastureRuntime) {
        val allies = runtime.slots.values.filterNot { it.entity.isRemoved }.map(::allyView)
        for (slot in runtime.slots.values) {
            val worker = slot.worker
            if (slot.rule != RecoveryRule.HEALER) continue
            val current = claims.claimOf(worker.pokemonId)
            val idle = worker.status == WorkerStatus.IDLE && current == null
            val preemptable = !worker.leashed && current?.kind == ClaimKind.WORK
            if (!idle && !preemptable) continue
            val traits = if (idle) refreshTraits(t, slot) else healerTraits(t, slot)
            if (!HealTargets.canStart(worker.stamina.fraction, worker.tired, t.settings, traits.combinedMultiplier, traits.isMajestic) ||
                !BrainBridge.canHoldOrder(slot.entity)
            ) {
                continue
            }
            val isFree: (UUID) -> Boolean = {
                val key = TargetKey.Entity(it)
                claims.isFree(key, worker.pokemonId) && !worker.isSkipped(key, t.now)
            }
            val ally = HealTargets.pick(worker.pokemonId, slot.entity.position(), allies, isFree, t.settings) ?: continue
            if (preemptable) {
                val shouldPreempt = DualFairyRule.shouldPreemptForHeal(
                    hasFairyType = hasFairyType(slot.pokemon),
                    leashed = worker.leashed,
                    holdsWorkClaim = true,
                    tired = worker.tired,
                    staminaFraction = worker.stamina.fraction,
                    allyNeedsHeal = true,
                    settings = t.settings,
                )
                if (!shouldPreempt) continue
                release(t, slot)
            }
            val granted = claims.tryClaim(worker.pokemonId, TargetKey.Entity(ally.id), ClaimKind.HEAL, runtime.key, t.now)
            if (granted is ClaimResult.Granted) t.machine.onHeal(worker, t.now)
        }
    }

    private fun allyView(slot: WorkerSlot): AllyView = AllyView(
        slot.worker.pokemonId,
        slot.entity.position(),
        hasFairyType(slot.pokemon),
        slot.worker.tired,
        slot.worker.stamina.fraction,
    )

    /** The auto-assign roles that a worker of the pasture can do. Leash-only roles need no scan. */
    private fun scanRoles(t: Tick, runtime: PastureRuntime): List<ActiveRole> {
        val types = runtime.slots.values.mapNotNull { slot -> scanTypesOf(t, slot).takeIf { it.isNotEmpty() } }
        if (types.isEmpty()) return emptyList()
        return t.roles.active.filter { role ->
            role.definition.autoAssign &&
                role.handler.scanKind != ScanKind.NONE &&
                types.any { RoleSelection.isEligible(role.definition, it) }
        }
    }

    private fun scanTypesOf(t: Tick, slot: WorkerSlot): Set<String> {
        if (slot.worker.leashed || slot.worker.tired || !BrainBridge.canHoldOrder(slot.entity)) return emptySet()
        return DualFairyRule.autoAssignTypes(
            types = typesOf(slot.pokemon),
            hasFairyType = hasFairyType(slot.pokemon),
            leashed = false,
            tired = false,
            staminaFraction = 0.0,
            allyNeedsHeal = false,
            settings = t.settings,
        )
    }

    private fun assign(t: Tick, runtime: PastureRuntime, found: List<FoundTarget>) {
        if (found.isEmpty()) return
        val allies by lazy { runtime.slots.values.filterNot { it.entity.isRemoved }.map(::allyView) }
        val allowedByActors = HashMap<List<UUID>, List<FoundTarget>>()
        val byKeyByActors = HashMap<List<UUID>, Map<TargetKey, FoundTarget>>()
        val breakByActors = HashMap<Pair<List<UUID>, BlockPos>, Boolean>()
        val taken = takenSpots(t)
        for (slot in runtime.slots.values) {
            val worker = slot.worker
            if (worker.leashed) continue
            val current = claims.claimOf(worker.pokemonId)
            val idle = current == null && worker.status == WorkerStatus.IDLE
            val moving = current?.kind == ClaimKind.WORK && t.machine.canMoveFromStandby(worker, t.now)
            if ((!idle && !moving) || !BrainBridge.canHoldOrder(slot.entity)) continue
            val fairy = hasFairyType(slot.pokemon)
            val allyNeedsHeal = fairy && HealTargets.pick(
                worker.pokemonId,
                slot.entity.position(),
                allies,
                { id ->
                    val key = TargetKey.Entity(id)
                    claims.isFree(key, worker.pokemonId) && !worker.isSkipped(key, t.now)
                },
                t.settings,
            ) != null
            val types = typesOf(slot.pokemon)
            val autoTypes = DualFairyRule.autoAssignTypes(
                types = types,
                hasFairyType = fairy,
                leashed = false,
                tired = worker.tired,
                staminaFraction = worker.stamina.fraction,
                allyNeedsHeal = allyNeedsHeal,
                settings = t.settings,
            )
            if (autoTypes.isEmpty()) continue
            val actors = actorsOf(runtime, slot)
            val actorKey = actors.filterNotNull().distinct()
            val byKey = byKeyByActors.getOrPut(actorKey) {
                val allowed = allowedByActors.getOrPut(actorKey) {
                    found.filter { LandClaims.actorsMayUse(actors, t.level, it.pos) }
                }
                allowed.flatMap { target ->
                    target.roles.groupBy { it.handler.claimLane }.map { (lane, roles) ->
                        TargetKey.block(t.dimension, target.pos, lane) to FoundTarget(target.pos, roles)
                    }
                }.toMap()
            }
            if (byKey.isEmpty()) continue
            val view = WorkerView(
                id = worker.pokemonId,
                pos = slot.entity.position(),
                types = types,
                hasFairyType = fairy,
                tired = worker.tired,
                staminaFraction = worker.stamina.fraction,
                allyNeedsHeal = allyNeedsHeal,
                settings = t.settings,
            ) { worker.isSkipped(it, t.now) }
            val candidates = byKey.map { (key, target) ->
                val mayClaimRole = { def: RoleDefinition ->
                    val active = target.roles.first { it.definition.id == def.id }
                    val cells = if (active.handler.breaksBlocks) {
                        guarded(active.handler, listOf(target.pos)) { active.handler.breakTargets(t.level, target.pos) }
                    } else {
                        emptyList()
                    }
                    val ok = BreakingClaimGate.mayClaimCells(mayUse = true, cells) { cell ->
                        breakByActors.getOrPut(actorKey to cell) { LandClaims.actorsMayBreak(actors, t.level, cell) }
                    }
                    if (!ok) worker.skipUntil[key] = t.now + t.settings.retryWaitTicks
                    ok
                }
                candidate(t, slot, autoTypes, key, target, mayClaimRole)
            }
            val isFree: (TargetKey) -> Boolean = { claims.isFree(it, worker.pokemonId) }
            val pick = if (current == null) {
                Assigner.pickForIdle(view, candidates, isFree)
            } else {
                Assigner.pickForStandbyMove(view, current.key, candidates, isFree)
            } ?: continue
            val target = byKey.getValue(pick.first.key)
            claim(t, runtime, slot, target.pos, target.roles.first { it.definition.id == pick.second.id }, taken, moving = current != null)
        }
    }

    /** Each role's demand is asked at most once for the candidate, with a fresh memo, because a scan candidate is no binding. */
    private fun candidate(
        t: Tick,
        slot: WorkerSlot,
        types: Set<String>,
        key: TargetKey,
        target: FoundTarget,
        mayClaimRole: (RoleDefinition) -> Boolean,
    ): CandidateView {
        val center = target.roles.first().handler.targetCenter(target.pos)
        val needsDemand = target.roles.all { it.handler.releasesWhenDone }
        val ask = { def: RoleDefinition ->
            val role = target.roles.first { it.definition.id == def.id }
            guarded(role.handler, false) { role.handler.hasDemand(context(t, slot, target.pos, role)) }
        }
        val asked = if (target.roles.size > 1) HashMap<ResourceLocation, Boolean>(target.roles.size) else null
        val demandOf = { def: RoleDefinition -> asked?.getOrPut(def.id) { ask(def) } ?: ask(def) }
        return CandidateView(key, center, target.roles.map { it.definition }, needsDemand, demandOf, mayClaimRole) {
            target.roles.any { role ->
                RoleSelection.isEligible(role.definition, types) && mayClaimRole(role.definition) && demandOf(role.definition)
            }
        }
    }

    private enum class Attempt { GRANTED, NO_SPOT, NOT_NOW, REFUSED }

    /**
     * A worker gets the claim only with a work spot that its navigation reaches (CD-ASG-09). With none, it skips the target for
     * `retryWaitTicks`, as after a travel timeout (CD-ASG-14). While its navigation cannot plan, for example in the air, it does
     * neither, and its next scan or poll tries again.
     */
    private fun claim(
        t: Tick,
        site: WorkSite,
        slot: WorkerSlot,
        target: BlockPos,
        role: ActiveRole,
        taken: MutableSet<Long>,
        moving: Boolean,
    ): Attempt {
        val worker = slot.worker
        val key = TargetKey.block(t.dimension, target, role.handler.claimLane)
        val shared = role.handler.sharesTargets
        if (shared && !claims.hasPlace(key)) return Attempt.REFUSED
        if (site is PastureRuntime && role.handler.breaksBlocks) {
            val cells = guarded(role.handler, listOf(target)) { role.handler.breakTargets(t.level, target) }
            val actors = actorsOf(site, slot)
            if (!BreakingClaimGate.mayClaimCells(mayUse = true, cells) { cell -> LandClaims.actorsMayBreak(actors, t.level, cell) }) {
                worker.skipUntil[key] = t.now + t.settings.retryWaitTicks
                return Attempt.REFUSED
            }
        }
        val own = worker.spot?.asLong()
        val entity = slot.entity
        val others = if (own == null) taken else taken - own
        val reach = Reach.of(entity)
        val spot = guarded(role.handler, null) { spotFor(t.level, site.area(t.settings), entity, role, target, others, reach) }
        if (spot == null) {
            if (reach.cannotPlan) return Attempt.NOT_NOW
            worker.skipUntil[key] = t.now + t.settings.retryWaitTicks
            return Attempt.NO_SPOT
        }
        if (moving) {
            release(t, slot)
            own?.let(taken::remove)
        }
        val granted = claims.tryClaim(worker.pokemonId, key, ClaimKind.WORK, site.pastureKey, t.now, shared)
        if (granted !is ClaimResult.Granted) return Attempt.REFUSED
        taken += spot.asLong()
        worker.target = target
        worker.spot = spot
        slot.role = role
        slot.memo = ClaimMemo()
        t.machine.onClaim(worker, t.now)
        guarded(role.handler, Unit) { role.handler.onClaim(context(t, slot, target, role, slot.memo)) }
        return Attempt.GRANTED
    }

    /** The engine's spot checks (CD-ASG-09), the handler's own rule and, with [reachable], a path from the worker. */
    private fun spotFor(
        level: ServerLevel,
        area: WorkArea,
        entity: PokemonEntity,
        role: ActiveRole,
        target: BlockPos,
        others: Set<Long>,
        reachable: (BlockPos) -> Boolean,
    ): BlockPos? {
        val height = entity.bbHeight.toDouble()
        val grid = LevelSpotGrid(level, role.handler, target, others, entity.bbWidth.toDouble(), height)
        // A cell in an unloaded chunk is not read, as LevelSpotGrid does not read one.
        val shape = TargetShape.around(target) { level.isLoaded(it) && role.handler.isPartOfTarget(level, target, it) }
        // CD-ASG-24: no spot puts the body where the target's tree grows, or in the column of any other sapling.
        val columns = GrowthColumns(role.handler.growthCells(level, target))
        val width = entity.bbWidth.toDouble()
        val sapling = BlockPos.MutableBlockPos()
        val isSapling = { x: Int, y: Int, z: Int ->
            level.isLoaded(sapling.set(x, y, z)) && level.getBlockState(sapling).`is`(BlockTags.SAPLINGS)
        }
        val allowed: (BlockPos) -> Boolean = {
            columns.allows(it, width, height) &&
                GrowthColumns.clearOfSaplings(it, width, height, isSapling) &&
                role.handler.allowsSpot(level, target, it, height)
        }
        val center = role.handler.targetCenter(target)
        return WorkSpots.findReachable(grid, area, shape, center, role.definition.range, entity.position(), reachable, allowed = allowed)
    }

    /** Step 5: arrival, the worker's state, and the handler calls of that state. Returns the event that the worker's machine gave. */
    private fun tickWorker(t: Tick, site: WorkSite, slot: WorkerSlot): MachineEvent? {
        val worker = slot.worker
        if (slot.entity.isRemoved) {
            drop(t, site, slot)
            return null
        }
        if (worker.status.isResting) {
            tickRest(t, slot)
            return null
        }
        if (worker.status == WorkerStatus.HEALING) {
            if (site is PastureRuntime) tickHeal(t, site, slot)
            return null
        }
        val claim = claims.claimOf(worker.pokemonId)
        if (claim == null) {
            // Another part of the mod released the claim, so the worker follows.
            if (worker.status != WorkerStatus.IDLE || worker.target != null) release(t, slot)
            return null
        }
        if (claim.kind != ClaimKind.WORK) return null
        if (site is PastureRuntime && !site.isPolled && !t.level.isLoaded(site.pos)) {
            release(t, slot)
            return null
        }
        val role = slot.role
        val target = worker.target
        val spot = worker.spot
        if (role == null || target == null || spot == null) {
            release(t, slot)
            return null
        }
        val handler = role.handler
        val ctx = context(t, slot, target, role, slot.memo)
        val before = worker.status
        var event: MachineEvent? = null
        // CD-ASG-25: one read before and one after the handler's calls, for a handler that grows, fells or replants a tree.
        val stateBefore = if (handler.growsTrees && t.level.isLoaded(target)) t.level.getBlockState(target) else null
        var misplaced = false
        val ok = guarded(handler, false) {
            // The machine reads arrival only while Traveling, so the handler is asked only then, after the engine's own rule.
            val arrived = isArrived(slot.entity, spot) || (before == WorkerStatus.TRAVELING && handler.acceptsArrival(ctx))
            event = t.machine.onTick(worker, t.now, arrived, { handler.hasDemand(ctx) }, claim.key, handler.releasesWhenDone)
            when (val e = event) {
                MachineEvent.StartedWorking -> {
                    handler.onStart(ctx)
                    misplaced = !snap(slot.entity, spot, handler.targetCenter(target))
                }

                MachineEvent.EnteredStandby -> when (before) {
                    WorkerStatus.WORKING -> handler.onStop(ctx)

                    WorkerStatus.TRAVELING -> {
                        misplaced = !snap(slot.entity, spot, handler.targetCenter(target))
                    }

                    else -> {}
                }

                is MachineEvent.ReleaseClaim -> {
                    // CD-CRP-10: a farming worker lets its crop go while Working, so the handler hears of the end of its work.
                    if (before == WorkerStatus.WORKING) handler.onStop(ctx)
                    claims.release(worker.pokemonId)
                    e.skip?.let { worker.skipUntil[it] = e.until }
                    clear(t, slot)
                }

                MachineEvent.MissedArrival -> cannotReach(t, slot, target)

                MachineEvent.SlackEnded -> {
                    val siteLoaded = site !is PastureRuntime || t.level.isLoaded(site.pos)
                    val valid = siteLoaded && t.level.isLoaded(target) && handler.stillValid(t.level, target)
                    if (!valid) {
                        claims.release(worker.pokemonId)
                        t.machine.onRelease(worker, t.now)
                        clear(t, slot)
                    } else if (site is PastureRuntime) {
                        site.requestPoll()
                    }
                }

                else -> {}
            }
            if (worker.status == WorkerStatus.WORKING) handler.applyWork(ctx)
            true
        }
        if (stateBefore != null) {
            val after = t.level.getBlockState(target)
            if (after != stateBefore && (isTree(stateBefore) || isTree(after))) freeAround(t, target)
        }
        // CD-ASG-25: a spot that a tree filled since the claim gets no snap. The worker leaves the tree, and a pasture worker claims again
        // with a new spot.
        if (misplaced) {
            freeSlot(t, site, slot, target)
            if (site is PastureRuntime && claims.claimOf(worker.pokemonId) != null) release(t, slot)
        }
        if (!ok) release(t, slot, callHandler = false)
        return event
    }

    /** The sites of [t]'s level: its pastures and its Quick Work orders. */
    private fun sitesOf(t: Tick): List<WorkSite> = runtimesOf(t.dimension) + orders.values.filter { it.level === t.level }

    /**
     * CD-ASG-25, CD-ASG-26: a tree grew at [pos], or a fell or a replant changed it. Each worker of the engine in the level whose body
     * lies near it, within [GROWTH_REACH] on x and z and [GROWTH_HEIGHT] up, and overlaps a block, leaves the tree ([free]). A pasture
     * worker whose spot the tree filled then claims again, with a new spot. A worker that rides or carries another entity stays. A throw
     * from another mod's block skips the rest and logs once, so the net never ends the server's tick.
     */
    private fun freeAround(t: Tick, pos: BlockPos) = safely {
        val near = AABB(pos).inflate(GROWTH_REACH, 0.0, GROWTH_REACH).expandTowards(0.0, GROWTH_HEIGHT, 0.0).expandTowards(0.0, -1.0, 0.0)
        for (site in sitesOf(t)) {
            for (slot in site.slots.values.toList()) {
                val entity = slot.entity
                if (entity.isRemoved || entity.level() !== t.level || !entity.boundingBox.intersects(near)) continue
                if (entity.isPassenger || entity.isVehicle) continue
                freeSlot(t, site, slot, pos)
                val spot = slot.worker.spot
                if (site is PastureRuntime && spot != null && claims.claimOf(slot.worker.pokemonId)?.kind == ClaimKind.WORK &&
                    !centerFits(entity, spot)
                ) {
                    release(t, slot)
                }
            }
        }
    }

    /** CD-ASG-27: a block whose check throws leaves the worker where it stands, with one warning for each cause. */
    private fun sidestepSafely(entity: PokemonEntity, spot: BlockPos, area: WorkArea) {
        try {
            sidestep(entity, spot, area)
        } catch (e: Exception) {
            unstepped.warn(e.javaClass.name) { "A wide worker stays stuck, because a block's check fails: $e" }
        } catch (e: LinkageError) {
            unstepped.warn(e.javaClass.name) { "A wide worker stays stuck, because a block's check fails: $e" }
        }
    }

    /**
     * CD-ASG-25: [free] for one worker of [site] from the tree at [tree], within its work area and the land that its owner may use.
     * CD-ASG-28: a worker that rests in place rests where the net put it. A sleeper lets its bed go, rests in place and is freed only when
     * its body overlaps a block of the changed tree (a natural leaf or a cell of its trunk) or its bed's sleep point lies in a tree, so no
     * rest order puts it back into the tree and no other block takes its bed. A throw from another mod's block skips this worker only.
     */
    private fun freeSlot(t: Tick, site: WorkSite, slot: WorkerSlot, tree: BlockPos) {
        val actors = when (site) {
            is PastureRuntime -> actorsOf(site, slot)
            is QuickRuntime -> listOf(site.owner)
        }
        val entity = slot.entity
        val worker = slot.worker
        val level = t.level
        val before = entity.position()
        val trunk = lazy { TreeWay.treeTrunk(TreeFactsReader(level)::facts, tree, entity.blockPosition()) }
        var bedLost = false
        safely {
            if (worker.status == WorkerStatus.SLEEPING) {
                val inChangedTree = overlaps(entity, entity.boundingBox.deflate(TOUCH)) { pos, state ->
                    isNaturalLeaf(state) || (isTrunkBlock(state) && pos in trunk.value)
                }
                bedLost = inChangedTree || worker.bed?.let { sleepsInTree(entity, it.center) } == true
                if (!bedLost) return@safely
            }
            free(entity, worker.spot, site.area(t.settings), trunk) { LandClaims.actorsMayUse(actors, level, it) }
        }
        when (worker.status) {
            WorkerStatus.RESTING_IN_PLACE -> if (entity.position() != before) worker.restSpot = entity.position()
            WorkerStatus.SLEEPING -> if (bedLost) restInPlace(t, slot)
            else -> {}
        }
    }

    /** CD-ASG-28, CD-BED-11: the worker of [slot] lets its bed go and rests in place where it stands. */
    private fun restInPlace(t: Tick, slot: WorkerSlot) {
        val worker = slot.worker
        if (claims.claimOf(worker.pokemonId)?.kind == ClaimKind.BED) claims.release(worker.pokemonId)
        t.machine.onRestTick(worker, t.now, atBed = false, bedPresent = false)
        worker.restSpot = slot.entity.position()
    }

    /** Runs [block], and logs a throw from another mod's block once, as a worker that stays in its tree (CD-ASG-25). */
    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            unfreed.warn(e.javaClass.name) { "A worker stays in a tree, because a block's check fails: $e" }
        } catch (e: LinkageError) {
            unfreed.warn(e.javaClass.name) { "A worker stays in a tree, because a block's check fails: $e" }
        }
    }

    /**
     * Work Stamina drains on each tick that the handler worked and recovers on every other tick, at the worker's rule (CD-STA-06,
     * CD-STA-07, CD-HEAL-12, CD-HEAL-13), times the bed's factor only while the worker lies on it (CD-BED-04). A worker that this
     * makes Tired stops in the same tick (CD-STA-13), and one that this fills gets up in the same tick (CD-BED-12). Then Work Stamina
     * is flushed when due, and the view follows (CD-STA-15).
     */
    private fun tickStamina(t: Tick, site: WorkSite, slot: WorkerSlot) {
        val worker = slot.worker
        val traits = refreshTraits(t, slot)
        if (worker.status == WorkerStatus.SLEEPING) {
            worker.bed?.tier?.let { slot.lastBedTier = it }
        }
        val ticks = t.now + 1 - worker.staminaUntil
        val workedThisTick = worker.status == WorkerStatus.WORKING && ticks > 0
        if (!t.settings.workXpEnabled || slot.pokemon.level >= Cobblemon.config.maxPokemonLevel) slot.xpTicks = 0L
        if (!t.settings.friendshipEnabled) slot.friendshipTicks = 0L
        if (ticks > 0) {
            val level = slot.pokemon.level
            when (worker.status) {
                WorkerStatus.WORKING -> {
                    val role = slot.role
                    val machineCapped = role == null || !WorkTraits.isOutputOrPace(role.definition.handler)
                    val scale = StaminaMath.drainScale(
                        isMajestic = traits.isMajestic,
                        roleMultiplier = traits.combinedMultiplier,
                        machineCapped = machineCapped,
                        hasDarkType = hasDarkType(slot.pokemon),
                        isNight = t.level.isNight,
                    )
                    worker.stamina.work(ticks, level, t.settings, drainFactor = scale, isAlpha = traits.isAlpha)
                }

                WorkerStatus.SLACKING -> {}

                else -> {
                    val bedTier = if (worker.status == WorkerStatus.SLEEPING) worker.bed?.tier else null
                    worker.stamina.rest(ticks, level, slot.rule, bedTier, t.settings)
                }
            }
            worker.staminaUntil = t.now + 1
        }
        if (workedThisTick) {
            tickXpAndFriendship(t, slot, ticks)
        }
        if (worker.tired && !worker.status.isResting) {
            when (site) {
                is PastureRuntime -> goToRest(t, site, slot)
                is QuickRuntime -> return endOrder(t, site, QuickEnd.TIRED)
            }
        } else if (!worker.tired && worker.status.isResting && site is PastureRuntime) {
            wake(t, site, slot)
        }
        if (worker.status == WorkerStatus.WORKING && !worker.tired) {
            tickSlack(t, site, slot, ticks.coerceAtLeast(1L))
        }
        if (FlushRule.isDue(slot.flushedStatus, worker.status, slot.flushedAt, t.now, t.settings)) flush(slot, t.now)
        syncView(slot)
    }

    private fun healerTraits(t: Tick, slot: WorkerSlot): EvaluatedTraits = WorkTraits.ofPokemon(
        pokemon = slot.pokemon,
        role = null,
        hasMate = slot.hasMate,
        fallbackTypes = typesOf(slot.pokemon),
        settings = t.settings,
    )

    private fun refreshTraits(t: Tick, slot: WorkerSlot): EvaluatedTraits {
        if (slot.role == null) return healerTraits(t, slot).also { slot.traits = it }
        return WorkTraits.ofPokemon(
            pokemon = slot.pokemon,
            role = slot.role?.definition,
            hasMate = slot.hasMate,
            fallbackTypes = emptySet(),
            settings = t.settings,
        ).also { slot.traits = it }
    }

    private fun tickXpAndFriendship(t: Tick, slot: WorkerSlot, workingTicks: Long) {
        val xpStep = WorkProgression.advanceXpTicks(
            currentTicks = slot.xpTicks,
            workingTicks = workingTicks,
            level = slot.pokemon.level,
            maxPokemonLevel = Cobblemon.config.maxPokemonLevel,
            s = t.settings,
        )
        slot.xpTicks = xpStep.nextTicks
        if (xpStep.xpAwarded > 0) {
            slot.pokemon.addExperience(WorkProgression.EXPERIENCE_SOURCE, xpStep.xpAwarded)
            refreshTraits(t, slot)
        }
        val owner = slot.pokemon.getOwnerUUID()?.let(server.playerList::getPlayer)?.takeIf { it.level() === t.level }
        val distSqr = owner?.position()?.distanceToSqr(slot.entity.position()) ?: Double.MAX_VALUE
        val friendshipStep = WorkProgression.advanceFriendshipTicks(
            currentTicks = slot.friendshipTicks,
            workingTicks = workingTicks,
            hasMate = slot.hasMate,
            ownerSameLevel = owner != null,
            ownerDistanceSqr = distSqr,
            s = t.settings,
        )
        slot.friendshipTicks = friendshipStep.nextTicks
        if (friendshipStep.friendshipAwarded > 0) {
            slot.pokemon.incrementFriendship(friendshipStep.friendshipAwarded, true)
        }
    }

    private fun tickSlack(t: Tick, site: WorkSite, slot: WorkerSlot, workingTicks: Long) {
        if (site !is PastureRuntime) return
        slot.slackTicks += workingTicks
        if (slot.slackTicks < t.settings.slackCheckTicks) return
        val role = slot.role ?: return
        val target = slot.worker.target ?: return
        val ctx = context(t, slot, target, role, slot.memo)
        if (!guarded(role.handler, true) { role.handler.canSlack(ctx) }) return
        slot.slackTicks = 0
        val prob = SlackMath.probability(
            baseChance = t.settings.baseSlackChance,
            effectiveNature = slot.pokemon.effectiveNature.name,
            baseNature = slot.pokemon.nature.name,
            areaMaxBedTier = maxBedTierInArea(t, site),
            lastSleptBedTier = slot.lastBedTier,
            friendship = slot.pokemon.friendship,
        )
        if (SlackMath.shouldSlack(prob, t.level.random.nextDouble())) {
            t.machine.onSlack(slot.worker, t.now)
            guarded(role.handler, Unit) { role.handler.onStop(ctx) }
            BrainBridge.clear(slot.entity)
            BrainBridge.endHold(slot.entity)
            removeFocus(slot)
        }
    }

    private fun maxBedTierInArea(t: Tick, runtime: PastureRuntime): Int {
        var best = 0
        for ((_, blockEntity) in t.world.blockEntitiesIn(runtime.area(t.settings))) {
            val bed = blockEntity as? BedBlockEntity ?: continue
            if (bed.tier.tier > best) best = bed.tier.tier
        }
        return best
    }

    /**
     * The work claim ends at once, through the release path, and a leash binding stays (CD-STA-13, CD-CLM-09). The worker then
     * claims the nearest free bed in its pasture's work area (CD-BED-05, CD-BED-07), or rests where it stands (CD-BED-08).
     */
    private fun goToRest(t: Tick, runtime: PastureRuntime, slot: WorkerSlot) {
        val worker = slot.worker
        release(t, slot)
        val bed = nearestFreeBed(t, runtime, slot)
        val granted = bed != null && claims.tryClaim(worker.pokemonId, bed.key, ClaimKind.BED, runtime.key, t.now) is ClaimResult.Granted
        t.machine.onTired(worker, t.now, bed.takeIf { granted })
        worker.restSpot = if (granted) null else slot.entity.position()
    }

    /** Beds have block entities, so the chunks' block entity maps hold every bed of the work area. Any size of Pokémon fits (CD-BED-09). */
    private fun nearestFreeBed(t: Tick, runtime: PastureRuntime, slot: WorkerSlot): BedCandidate? {
        val actors = actorsOf(runtime, slot)
        val beds = t.world.blockEntitiesIn(runtime.area(t.settings)).mapNotNull { (pos, blockEntity) ->
            val bed = blockEntity as? BedBlockEntity ?: return@mapNotNull null
            if (!LandClaims.actorsMayUse(actors, t.level, pos)) return@mapNotNull null
            if (sleepsInTree(slot.entity, BedCandidate.sleepPoint(pos))) return@mapNotNull null
            BedCandidate(TargetKey.block(t.dimension, pos), BedCandidate.sleepPoint(pos), bed.tier.tier)
        }.toList()
        return BedSelector.pick(slot.entity.position(), beds) { claims.isFree(it, slot.worker.pokemonId) }
    }

    /** ToBed and Sleeping: the walk to the bed, and the bed under the sleeper, which may break (CD-BED-11). */
    private fun tickRest(t: Tick, slot: WorkerSlot) {
        val worker = slot.worker
        val bed = worker.bed
        if (worker.status == WorkerStatus.RESTING_IN_PLACE || bed == null) return
        val entity = slot.entity
        val claim = claims.claimOf(worker.pokemonId)
        val held = claim?.kind == ClaimKind.BED && claim.key == bed.key
        val present = held && isBed(t.level, bed.key)
        val atBed = present && BedSelector.isAt(entity.position(), entity.bbWidth.toDouble(), bed.center)
        when (t.machine.onRestTick(worker, t.now, atBed, present)) {
            // CD-ASG-28: a bed whose sleep point a tree filled since the claim gives no rest.
            MachineEvent.FellAsleep -> if (sleepsInTree(entity, bed.center)) restInPlace(t, slot) else lieDown(entity, bed.center)

            MachineEvent.LostBed -> {
                if (held) claims.release(worker.pokemonId)
                worker.restSpot = entity.position()
            }

            else -> {}
        }
    }

    /**
     * A healer walks to its ally and heals it once it is within 2 blocks of the ally's body (CD-HEAL-10): the healer pays 25
     * points, Tired at 0%, and the ally is full (CD-HEAL-04, CD-HEAL-05). An ally that stops needing the heal, leaves the pasture or
     * becomes a Fairy-type, and a healer that gets a binding, cancel the heal at no cost (CD-HEAL-11). The ally gets up, and a spent
     * healer lies down, in their stamina steps (CD-BED-12, CD-HEAL-17).
     */
    private fun tickHeal(t: Tick, runtime: PastureRuntime, slot: WorkerSlot) {
        val worker = slot.worker
        val claim = claims.claimOf(worker.pokemonId)?.takeIf { it.kind == ClaimKind.HEAL }
        val allyId = (claim?.key as? TargetKey.Entity)?.uuid
        val ally = allyId?.let { runtime.slots[it] }?.takeUnless { it.entity.isRemoved }
        val qualifies = ally != null && slot.rule == RecoveryRule.HEALER && HealTargets.qualifies(allyView(ally), t.settings)
        val here = slot.entity.position()
        val body = ally?.entity?.boundingBox
        val inRange = body != null && qualifies && HealTransaction.inRange(here, HealTransaction.nearestPoint(body, here))
        val event = t.machine.onHealTick(worker, t.now, qualifies, inRange) ?: return
        claims.release(worker.pokemonId)
        clear(t, slot)
        if (event == MachineEvent.Healed && ally != null) {
            val traits = refreshTraits(t, slot)
            HealTransaction.complete(worker.stamina, ally.worker.stamina, t.settings, traits.combinedMultiplier, traits.isMajestic)
            EffectPlayer.playHeal(t.level, slot.entity, ally.entity)
        } else if (event is MachineEvent.HealCancelled && event.timedOut && allyId != null) {
            worker.skipUntil[TargetKey.Entity(allyId)] = t.now + t.settings.retryWaitTicks
        }
    }

    /**
     * At 100%: the bed claim ends in the same tick (CD-BED-12), and a leashed worker claims its bound target again (CD-STA-14). The
     * rest's hold stops the worker's body with no fall, so the navigation of most species cannot plan in this tick, and the next poll
     * claims instead (CD-ASG-09).
     */
    private fun wake(t: Tick, runtime: PastureRuntime, slot: WorkerSlot) {
        val worker = slot.worker
        if (claims.claimOf(worker.pokemonId)?.kind == ClaimKind.BED) claims.release(worker.pokemonId)
        worker.restSpot = null
        val event = t.machine.onRested(worker, t.now)
        clear(t, slot)
        if (event is MachineEvent.Rested && event.toBinding) claimBound(t, runtime, slot, takenSpots(t))
    }

    private fun isBed(level: ServerLevel, key: TargetKey): Boolean {
        val pos = BlockPos.of((key as? TargetKey.Block ?: return false).pos)
        return level.isLoaded(pos) && level.getBlockState(pos).block is BedBlock
    }

    /** To the instance that the last poll read from the PC, which is the PC's own until Cobblemon reloads the store. */
    private fun flush(slot: WorkerSlot, now: Long) {
        StaminaStore.save(slot.pokemon, slot.worker.stamina, slot.worker.staminaUntil)
        slot.flushedStatus = slot.worker.status
        slot.flushedAt = now
    }

    /** A new entity has no view yet, so it gets one at once. */
    private fun syncView(slot: WorkerSlot) {
        val entity = slot.entity
        if (entity.isRemoved) return
        val stamina = slot.worker.stamina
        val status = slot.worker.status
        if (slot.viewEntity === entity && !ViewSync.shouldSync(slot.viewPercent, stamina.percent, slot.viewStatus, status)) return
        entity.setData(DynamicsAttachments.STAMINA_VIEW, StaminaView.of(stamina, status))
        slot.viewEntity = entity
        slot.viewPercent = stamina.percent
        slot.viewStatus = status
    }

    /**
     * Step 6: the work order goes into the Brain on every tick, because Cobblemon rebuilds Brains (CD-AI-04). A Working worker
     * has its focus entity and its effect loop, and any other worker has no focus entity, from this tick on (CD-FX-07).
     */
    private fun direct(t: Tick, site: WorkSite, slot: WorkerSlot) {
        val worker = slot.worker
        if (worker.status.isResting) {
            removeFocus(slot)
            directRest(t, slot)
            return
        }
        if (worker.status == WorkerStatus.HEALING) {
            removeFocus(slot)
            directHeal(slot)
            return
        }
        val claim = claims.claimOf(worker.pokemonId)
        val role = slot.role
        val target = worker.target
        val spot = worker.spot
        val entity = slot.entity
        if (claim?.kind != ClaimKind.WORK || role == null || target == null || spot == null || entity.isRemoved) {
            removeFocus(slot)
            return
        }
        if (worker.status == WorkerStatus.SLACKING) {
            removeFocus(slot)
            val area = site.area(t.settings)
            if (area.contains(entity.blockPosition())) {
                BrainBridge.clear(entity)
                constrainSlackWander(entity, area)
            } else {
                BrainBridge.write(entity, WorkOrder(Vec3.atBottomCenterOf(spot), null, hold = false))
            }
            return
        }
        val center = guarded(role.handler, null) { role.handler.targetCenter(target) }
        if (worker.status == WorkerStatus.TRAVELING) {
            // The watch takes every Traveling tick, because the navigation reports no stuck worker in flight or in water.
            val stalled = slot.stall.stalled(t.now, entity.x, entity.y, entity.z)
            // CD-ASG-27: a worker 1 block wide or wider that pushes against a block on each of 20 ticks steps without the full stall.
            val wide = entity.bbWidth >= 1f
            val pushing = entity.horizontalCollision
            val pressed = wide && slot.press.stalled(t.now, entity.x, entity.y, entity.z, pushing)
            if (stalled || pressed || entity.navigation.isStuck) {
                if (!wide) {
                    unstick(entity)
                } else if (pushing && Unstick.sidestepDue(t.now, slot.lastSidestepTick)) {
                    slot.lastSidestepTick = t.now
                    sidestepSafely(entity, spot, site.area(t.settings))
                }
            }
        }
        BrainBridge.write(entity, WorkOrder(Vec3.atBottomCenterOf(spot), center, hold = isArrived(entity, spot)))
        if (!BrainBridge.holdsOrder(entity)) {
            val species = slot.pokemon.species.resourceIdentifier
            unbridged.warn(species) { "$species has a baseAI that skips Cobblemon's auto behaviours, so it cannot work" }
            worker.skipUntil[claim.key] = t.now + t.settings.retryWaitTicks
            release(t, slot)
            return
        }
        if (worker.status != WorkerStatus.WORKING || center == null) {
            removeFocus(slot)
            return
        }
        val focus = slot.focus?.takeUnless { it.isRemoved } ?: EffectPlayer.spawnFocus(t.level, center).also { slot.focus = it }
        EffectPlayer.tickWorking(t.level, entity, focus, role.definition, worker.statusSince, t.now, slot.traits.masteryMoves)
    }

    /**
     * A worker in ToBed walks to its bed. One that sleeps or rests in place holds its sleep point with the sleep pose and never
     * Cobblemon's battle sleep status (CD-BED-10, CD-AI-07), and loops the sleep effect (CD-FX-10).
     */
    private fun directRest(t: Tick, slot: WorkerSlot) {
        val worker = slot.worker
        val entity = slot.entity
        if (entity.isRemoved) return
        val order = when (worker.status) {
            WorkerStatus.TO_BED -> worker.bed?.let { WorkOrder(it.center, null, hold = false) }
            WorkerStatus.SLEEPING -> worker.bed?.let { WorkOrder(it.center, null, hold = true, sleep = true) }
            else -> worker.restSpot?.let { WorkOrder(it, null, hold = true, sleep = true) }
        } ?: return
        BrainBridge.write(entity, order)
        if (order.sleep) EffectPlayer.tickSleeping(t.level, entity, worker.statusSince, t.now)
    }

    /** A healer's order is an approach to its ally, which it looks at. */
    private fun directHeal(slot: WorkerSlot) {
        val entity = slot.entity
        val allyId = (claims.claimOf(slot.worker.pokemonId)?.key as? TargetKey.Entity)?.uuid ?: return
        val ally = slots[allyId]?.entity?.takeUnless { it.isRemoved } ?: return
        if (!entity.isRemoved) BrainBridge.write(entity, WorkOrder(ally.position(), ally.boundingBox.center, hold = false))
    }

    private fun constrainSlackWander(entity: PokemonEntity, area: WorkArea) {
        val brain = entity.brain
        val walk = brain.getMemory(MemoryModuleType.WALK_TARGET).orElse(null)
        if (walk != null) {
            val goal = walk.target.currentBlockPosition()
            if (!area.contains(goal)) {
                entity.navigation.stop()
                val clamped = SlackMath.clampWanderTarget(goal, area)
                if (clamped == entity.blockPosition()) {
                    brain.eraseMemory(MemoryModuleType.WALK_TARGET)
                } else {
                    brain.setMemory(
                        MemoryModuleType.WALK_TARGET,
                        WalkTarget(Vec3.atBottomCenterOf(clamped), walk.speedModifier, walk.closeEnoughDist),
                    )
                }
                return
            }
        }
        val navTarget = entity.navigation.targetPos
        if (navTarget != null && !area.contains(navTarget)) {
            entity.navigation.stop()
        }
    }

    /** CD-PERF-04: a focus entity lives only while its worker is Working. */
    private fun removeFocus(slot: WorkerSlot) {
        slot.focus?.let(EffectPlayer::removeFocus)
        slot.focus = null
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

    /** CD-QWK-06 to CD-QWK-11: the refusals, then a normal work claim through the engine's own spot search and path check. */
    private fun start(
        t: Tick,
        owner: ServerPlayer,
        pokemon: Pokemon,
        entity: PokemonEntity,
        aim: QuickAim,
        role: ActiveRole,
        name: Component,
    ) {
        // CD-QWK-13: an owner who walked past the leash while the Pokémon came out gets the leash's message, and no order starts.
        if (QuickWorkRules.beyondLeash(AABB(aim.pos).distanceToSqr(owner.eyePosition), t.settings.quickWorkLeashRange)) {
            return tell(aim.owner, QuickEnd.TOO_FAR.messageKey, pokemon, name)
        }
        val canonical = guarded(role.handler, aim.pos) { role.handler.canonical(t.level, aim.pos) }
        if (!guarded(role.handler, false) { role.handler.stillValid(t.level, canonical) }) return
        // CD-STA-11: a party Pokémon recovers for its time away when it loads.
        val worker = Worker(pokemon.uuid, pasture = null)
        worker.stamina = StaminaStore.load(pokemon, t.now, t.settings)
        worker.staminaUntil = t.now
        val slot = WorkerSlot(worker, entity, pokemon)
        val center = guarded(role.handler, Vec3.atCenterOf(canonical)) { role.handler.targetCenter(canonical) }
        val area = QuickRuntime.areaAround(center, role.definition.range)
        val order = QuickRuntime(t.level, aim.owner, aim.pos, canonical, t.now, name, slot, area)
        // CD-QWK-32: a refusal goes out through tell after the claim mods' checks, so the engine's message is the last one in this tick.
        val refusal = QuickWorkRules.refusal(
            mayUse = mayUse(owner, t.level, canonical),
            tired = worker.tired,
            // CD-CRP-13: a claim or a binding of the other farming role leaves the crop free for this one.
            targetFree = claims.isFree(TargetKey.block(t.dimension, canonical, role.handler.claimLane), pokemon.uuid),
            // CD-QWK-36: the Muse's order starts while the table's menu is closed, because R does nothing while a screen is open.
            waitsForDemand = role.definition.quickWork == true,
        ) { guarded(role.handler, false) { role.handler.hasDemand(context(t, slot, canonical, role)) } }
        if (refusal != null) return tell(aim.owner, refusal, pokemon, name)
        when (claim(t, order, slot, canonical, role, takenSpots(t), moving = false)) {
            Attempt.GRANTED -> {
                orders[pokemon.uuid] = order
                slots[pokemon.uuid] = slot
                tell(aim.owner, QuickWorkRules.STARTED, pokemon, name)
            }

            Attempt.REFUSED -> tell(aim.owner, QuickWorkRules.CLAIMED, pokemon, name)

            // No work spot that a path reaches (CD-QWK-10). An order never searches again, so the owner learns why now.
            else -> tell(aim.owner, QuickWorkRules.CANNOT_REACH, pokemon, name)
        }
    }

    /**
     * CD-QWK-06, CD-QWK-26: the permission of an order. The start and every 20th tick of the order ask it (CD-QWK-29). [owner] is the
     * player that `PlayerList.getPlayer` returned for the order's owner (CD-QWK-31).
     */
    private fun mayUse(owner: ServerPlayer, level: ServerLevel, pos: BlockPos): Boolean =
        !owner.isSpectator && owner.mayInteract(level, pos) && LandClaims.mayUse(owner, level, pos)

    /** Every Quick Work message goes to the order's owner only, with the Pokémon's name and the target's name (CD-QWK-25). */
    private fun tell(owner: UUID, key: String, pokemon: Pokemon, target: Component) =
        Feedback.actionBar(owner, server, Component.translatable(key, pokemon.getDisplayName(), target))

    /** One tick of an order: its end checks, then its worker's own steps, as a pasture worker's (CD-QWK-13 to CD-QWK-23). */
    private fun tickOrder(t: Tick, order: QuickRuntime) {
        val slot = order.slot
        val worker = slot.worker
        val entity = slot.entity
        val owner = server.playerList.getPlayer(order.owner)
        val watch = QuickWorkRules.Watch(
            entityGone = !entity.isAlive || entity.beamMode == QuickWorkRules.RECALL_BEAM || slot.pokemon.entity !== entity,
            battling = entity.isBattling,
            ownerPresent = owner != null && owner.level() === t.level,
            ownerDistanceSqr = owner?.let { AABB(order.aimed).distanceToSqr(it.eyePosition) } ?: Double.MAX_VALUE,
            leashRange = t.settings.quickWorkLeashRange,
            holdsClaim = claims.claimOf(worker.pokemonId)?.kind == ClaimKind.WORK,
            status = worker.status,
            ticksInStatus = t.now - worker.statusSince,
            standbyLimit = t.settings.standbyMoveTicks,
            waitsForDemand = slot.role?.definition?.quickWork == true,
            endsWhenDone = slot.role?.handler?.releasesWhenDone == true,
        )
        QuickWorkRules.endReason(watch)?.let { return endOrder(t, order, it) }
        // CD-QWK-29: the end rule passed, so the owner is online and in the order's level, which the FTB Chunks check needs.
        if (owner != null && QuickWorkRules.permissionDue(order.startedAt, t.now) && !mayUse(owner, t.level, order.canonical)) {
            return endOrder(t, order, QuickEnd.PERMISSION)
        }
        // CD-QWK-22: the machine released the claim of a worker whose travel timed out, so the order ends, and it never tries again.
        if (tickWorker(t, order, slot) is MachineEvent.ReleaseClaim) return endOrder(t, order, QuickEnd.UNREACHABLE)
        if (orders[worker.pokemonId] === order) tickStamina(t, order, slot)
    }

    /**
     * The order ends for good (CD-QWK-14): the release path, with `onStop` for a Working worker, a flush of Work Stamina into the party
     * Pokémon's data, and no slot, order or view left. The Brain bridge cleared the work order, so Cobblemon's follow-owner task takes
     * over (CD-QWK-15).
     */
    private fun endOrder(t: Tick, order: QuickRuntime, reason: QuickEnd) {
        drop(t, order, order.slot)
        orders.remove(order.slot.worker.pokemonId)
        BrainBridge.endHold(order.slot.entity)
        tell(order.owner, reason.messageKey, order.slot.pokemon, order.targetName)
    }

    /** The worker leaves: its Work Stamina is saved, which covers entity unload, and a Pokémon that no longer works shows no bar. */
    private fun drop(t: Tick, site: WorkSite, slot: WorkerSlot) {
        release(t, slot)
        flush(slot, t.now)
        WorkerEvents.left(slot.pokemon)
        val entity = slot.entity
        if (!entity.isRemoved && entity.hasData(DynamicsAttachments.STAMINA_VIEW)) entity.removeData(DynamicsAttachments.STAMINA_VIEW)
        site.slots.remove(slot.worker.pokemonId)
        slots.remove(slot.worker.pokemonId)
    }

    /** The release path: `onStop` when the worker is Working, then Idle with no target, spot or role. */
    private fun release(t: Tick, slot: WorkerSlot, callHandler: Boolean = true) {
        val worker = slot.worker
        claims.release(worker.pokemonId)
        // CD-LSH-34: the memos of an auto worker's switch roles belong to its claim.
        if (slot.binding == null) slot.probes.clear()
        val role = slot.role
        val target = worker.target
        if (callHandler && worker.status == WorkerStatus.WORKING && role != null && target != null) {
            guarded(role.handler, Unit) { role.handler.onStop(context(t, slot, target, role, slot.memo)) }
        }
        t.machine.onRelease(worker, t.now)
        clear(t, slot)
    }

    /**
     * The worker has no order any more, and a worker that lay down has its own pose back (CD-AI-08). Every end of a work claim comes
     * here: a release, a drop, a timeout, a Tired worker and a failed handler. So the handler hears of each end once.
     */
    private fun clear(t: Tick, slot: WorkerSlot) {
        if (slot.role?.handler === SentryHandler) {
            val pasture = slot.worker.pasture ?: slot.worker.target?.let { PastureKey(t.dimension, it.asLong()) }
            if (pasture != null) SentryWatch.unwatch(pasture)
        }
        unclaim(t, slot, slot.entity)
        slot.worker.target = null
        slot.worker.spot = null
        slot.worker.dropExpired(t.now)
        slot.role = null
        slot.memo = ClaimMemo()
        slot.slackTicks = 0
        BrainBridge.clear(slot.entity)
        BrainBridge.wake(slot.entity)
        removeFocus(slot)
    }

    /** `onUnclaim` for [entity], while [slot] holds a work claim. */
    private fun unclaim(t: Tick, slot: WorkerSlot, entity: PokemonEntity) {
        val role = slot.role ?: return
        val target = slot.worker.target ?: return
        guarded(role.handler, Unit) { role.handler.onUnclaim(context(t, slot, target, role, slot.memo, entity)) }
    }

    /**
     * [memo] is the slot's for its own claim. A target that the engine only weighs for the worker gets an empty one. A binding gives its
     * destination and faces only to its own target and role (CD-LSH-18). [entity] differs from the slot's only for the old entity of a
     * claim that moves to the Pokémon's new entity.
     */
    private fun context(
        t: Tick,
        slot: WorkerSlot,
        target: BlockPos,
        role: ActiveRole,
        memo: ClaimMemo = ClaimMemo(),
        entity: PokemonEntity = slot.entity,
    ): WorkContext {
        val binding = slot.binding?.takeIf { it.target == target && it.roleId == role.definition.id }
        val stalls = slot.worker.stalledUntil
        val key = { pos: BlockPos -> TargetKey.block(t.dimension, pos, role.handler.claimLane) }
        val eval = WorkTraits.ofPokemon(slot.pokemon, role.definition, slot.hasMate, settings = t.settings)
        if (slot.role === role) slot.traits = eval
        val machineCapped = !WorkTraits.isOutputOrPace(role.definition.handler)
        val drainScale = StaminaMath.drainScale(
            isMajestic = eval.isMajestic,
            roleMultiplier = eval.combinedMultiplier,
            machineCapped = machineCapped,
            hasDarkType = hasDarkType(slot.pokemon),
            isNight = t.level.isNight,
        )
        return WorkContext(
            t.level,
            target,
            entity,
            slot.pokemon,
            role.definition,
            t.now,
            destination = binding?.destination,
            sourceFace = binding?.face,
            destinationFace = binding?.destinationFace,
            pasture = slot.worker.pasture?.let { BlockPos.of(it.pos) },
            memo = memo,
            spot = slot.worker.spot,
            spend = { ticks ->
                slot.worker.stamina.work(
                    ticks.toLong(),
                    slot.pokemon.level,
                    t.settings,
                    drainFactor = drainScale,
                    isAlpha = eval.isAlpha,
                )
            },
            mayChange = { pos -> mayChange(t, slot, pos) },
            mayBreak = { pos -> mayBreak(t, slot, pos) },
            stalled = { ticks -> stalls[key(target)] = t.now + ticks },
            stalledAt = { pos, ticks -> stalls[key(pos)] = t.now + ticks },
            isStalled = { pos -> slot.worker.isStalled(key(pos), t.now) },
            area = siteOf(slot)?.area(t.settings),
            laneFree = { pos -> claims.isFree(key(pos), slot.worker.pokemonId) },
            multiplier = eval.combinedMultiplier,
            isAlpha = eval.isAlpha,
        )
    }

    /**
     * CD-HRV-13: the land claim checks of a change at [pos] by the worker of [slot], in this tick: the pasture check of the placer that
     * the last poll read for a pasture worker, and the owner's checks for a Quick Worker, whose owner is online in the order's level.
     */
    private fun mayChange(t: Tick, slot: WorkerSlot, pos: BlockPos): Boolean {
        slot.worker.pasture?.let { key -> return pastures[key]?.let { LandClaims.actorsMayUse(actorsOf(it, slot), t.level, pos) } == true }
        val owner = orders[slot.worker.pokemonId]?.owner?.let(server.playerList::getPlayer) ?: return false
        return owner.level() === t.level && mayUse(owner, t.level, pos)
    }

    /** CD-HRV-16, CD-TEAM-07: the break question of each claim mod about [pos], across the worker's actors or for the online Quick Worker owner. */
    private fun mayBreak(t: Tick, slot: WorkerSlot, pos: BlockPos): Boolean {
        slot.worker.pasture?.let { key ->
            return pastures[key]?.let { LandClaims.actorsMayBreak(actorsOf(it, slot), t.level, pos) } == true
        }
        val owner = orders[slot.worker.pokemonId]?.owner?.let(server.playerList::getPlayer) ?: return false
        return owner.level() === t.level && LandClaims.mayBreak(owner, t.level, pos)
    }

    /** CD-CLM-11: only a binding of an exclusive role reserves its target, and a farming binding only in its own lane (CD-CRP-07). */
    private fun reservedFor(key: TargetKey): UUID? {
        val block = key as? TargetKey.Block ?: return null
        val level = server.getLevel(ResourceKey.create(Registries.DIMENSION, block.dimension)) ?: return null
        val index = level.getExistingDataOrNull(DynamicsAttachments.LEASH_INDEX) ?: return null
        return index.exclusiveAt(BlockPos.of(block.pos), block.lane, RoleRegistry::sharesTargets, RoleRegistry::laneOf)?.pokemonId
    }

    companion object {
        private const val CANNOT_REACH = "cobblemon_dynamics.leash.cannot_reach"

        /** A body that only touches a block is not inside it. */
        private const val TOUCH = 1e-3

        /** CD-ASG-27: how far ahead of a face of a stuck worker's body a block counts as pressed against it. */
        private const val PROBE = 0.05

        /** CD-LND-12: the most checks of the claim mods that the net of CD-ASG-25 asks for one worker. */
        private const val MAX_CLAIM_ASKS = 4

        /** CD-ASG-26: at most this many growths wait for the next tick of their level. */
        private const val MAX_GROWTHS = 256

        /** CD-ASG-25: how far from a grown tree's base, on x and z, a worker's body may lie for the net to look at it. */
        private const val GROWTH_REACH = 6.0

        /** CD-ASG-25: how far above a grown tree's base a worker's body may lie for the net to look at it. */
        private const val GROWTH_HEIGHT = 40.0

        /** Two pasture polls, so that the wander pause of a worker that waits for its crop never lapses between polls (CD-CRP-09). */
        private const val WAIT_PAUSE_TICKS = 2L * PastureRuntime.POLL_TICKS

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

        /** Where navigation stops [entity] for [spot], by the rule of [Arrival]. */
        fun isArrived(entity: Entity, spot: BlockPos): Boolean = Arrival.isAt(entity.x, entity.y, entity.z, entity.bbWidth.toDouble(), spot)

        /**
         * A stuck traveler narrower than a block goes to the middle of its cell when its body fits there, or else to that middle on top
         * of the block in the cell, such as the pasture block's top half, when its body fits there ([Unstick]). A wider one steps
         * sideways instead ([sidestep]).
         */
        private fun unstick(entity: PokemonEntity) {
            val (x, z) = Unstick.middle(entity.x, entity.z, entity.bbWidth.toDouble()) ?: return
            if (fits(entity, x, entity.y, z)) {
                entity.teleportTo(x, entity.y, z)
                return
            }
            val level = entity.level()
            val cell = BlockPos.containing(x, entity.y, z)
            val top = level.getBlockState(cell).getCollisionShape(level, cell).max(Direction.Axis.Y)
            val y = Unstick.onTop(entity.y, cell.y, top) ?: return
            if (fits(entity, x, y, z)) entity.teleportTo(x, y, z)
        }

        /**
         * CD-ASG-27: a stuck traveler 1 block wide or wider that presses a face of its body against blocks higher than its step, on the
         * side that it travels to, moves sideways by the shortest of [Unstick.sideShifts] that clears them, at most half its width, so its
         * center stays inside the space that its body held. It travels to its path's next point, or with no path to where navigation
         * stops it at [spot] ([Arrival.stopOffset]). Of the faces that lead there ([Unstick.leadingFaces]), the one that leads most
         * directly comes first. The new place needs room for the body and no block ahead of that face, its center inside [area] in a
         * walkable or open cell, or one beside water, over a sturdy floor, as [free] asks of its cell, and no open fluid or hazard under each part of the body at its
         * feet. A worker that rides or carries another entity stays, and so does one for which no shift passes. The engine runs
         * this on a stall of a worker that pushes against a block, at most once in 20 ticks for each worker.
         */
        private fun sidestep(entity: PokemonEntity, spot: BlockPos, area: WorkArea) {
            if (entity.isPassenger || entity.isVehicle) return
            val level = entity.level()
            val box = entity.boundingBox
            val low = box.minY + entity.maxUpStep() + TOUCH
            if (low >= box.maxY - TOUCH) return
            val stop = Arrival.stopOffset(entity.bbWidth.toDouble())
            val toward =
                entity.navigation.path?.takeUnless { it.isDone }?.getNextEntityPos(entity) ?: Vec3(spot.x + stop, 0.0, spot.z + stop)
            val dx = toward.x - entity.x
            val dz = toward.z - entity.z
            val body = AABB(box.minX + TOUCH, low, box.minZ + TOUCH, box.maxX - TOUCH, box.maxY - TOUCH, box.maxZ - TOUCH)
            for (face in Unstick.leadingFaces(dx, dz)) {
                val ahead = body.move(face.stepX * PROBE, 0.0, face.stepZ * PROBE)
                val blocks = level.getBlockCollisions(entity, ahead).flatMap { it.toAabbs() }.filter { it.intersects(ahead) }
                if (blocks.isEmpty()) continue
                val side = face.clockWise.axis
                val shifts = Unstick.sideShifts(
                    box.min(side),
                    box.max(side),
                    blocks.minOf { it.min(side) },
                    blocks.maxOf { it.max(side) },
                    entity.bbWidth / 2.0,
                )
                for (shift in shifts) {
                    val move = if (side == Direction.Axis.X) Vec3(shift, 0.0, 0.0) else Vec3(0.0, 0.0, shift)
                    if (canSidestep(entity, ahead.move(move), move, area)) {
                        entity.teleportTo(entity.x + move.x, entity.y, entity.z + move.z)
                        entity.deltaMovement = Vec3.ZERO
                        return
                    }
                }
            }
        }

        /**
         * A body moved by [move] fits and has no block in [ahead]. Its center lies inside [area] in a walkable or open cell over a
         * sturdy floor ([Unstick.isOpenFoot]), and each cell under the body at its feet holds no open fluid ([Unstick.isDryFoot]) and no
         * hazard ([Unstick.isSafeFoot]).
         */
        private fun canSidestep(entity: PokemonEntity, ahead: AABB, move: Vec3, area: WorkArea): Boolean {
            val level = entity.level()
            val x = entity.x + move.x
            val z = entity.z + move.z
            val center = BlockPos.containing(x, entity.y, z)
            if (!area.contains(center)) return false
            if (!fits(entity, x, entity.y, z) || !level.noBlockCollision(entity, ahead)) return false
            val below = center.below()
            if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) return false
            val moved = entity.boundingBox.move(move)
            if (!Unstick.isOpenFoot(WalkNodeEvaluator.getPathTypeStatic(entity, center))) return false
            return Unstick.footCells(moved.minX + TOUCH, moved.minZ + TOUCH, moved.maxX - TOUCH, moved.maxZ - TOUCH, center.y).all { cell ->
                val dry = Unstick.isDryFoot(
                    !level.getFluidState(cell).isEmpty,
                    !level.getBlockState(cell).getCollisionShape(level, cell).isEmpty,
                )
                dry && Unstick.isSafeFoot(WalkNodeEvaluator.getPathTypeStatic(entity, cell))
            }
        }

        /** A leaf that a tree grew, which [free]'s fill may pass. A leaf with no `persistent` property counts as placed. */
        private fun isNaturalLeaf(state: BlockState): Boolean =
            state.`is`(BlockTags.LEAVES) && !state.getOptionalValue(LeavesBlock.PERSISTENT).orElse(true)

        private fun isRoot(state: BlockState): Boolean = state.`is`(Blocks.MANGROVE_ROOTS) || state.`is`(Blocks.MUDDY_MANGROVE_ROOTS)

        /** A log or a mangrove root, which may be a cell of a tree's trunk. */
        private fun isTrunkBlock(state: BlockState): Boolean = state.`is`(BlockTags.LOGS) || isRoot(state)

        /** Whether a block in [box] that [test] accepts has a collision shape that meets [box]. */
        private fun overlaps(entity: PokemonEntity, box: AABB, test: (BlockPos, BlockState) -> Boolean): Boolean {
            val level = entity.level()
            val min = BlockPos.containing(box.minX, box.minY, box.minZ)
            val max = BlockPos.containing(box.maxX, box.maxY, box.maxZ)
            for (pos in BlockPos.betweenClosed(min, max)) {
                if (!level.isLoaded(pos)) continue
                val state = level.getBlockState(pos)
                if (!test(pos, state)) continue
                if (state.getCollisionShape(level, pos).toAabbs().any { it.move(pos).intersects(box) }) return true
            }
            return false
        }

        /**
         * CD-ASG-28: whether the body of [entity] on [point], a bed's sleep point, overlaps a log, a leaf or a mangrove root. Any other block
         * keeps the bed, so a Cobblemon of any size still sleeps on it (CD-BED-09).
         */
        private fun sleepsInTree(entity: PokemonEntity, point: Vec3): Boolean {
            val box = entity.boundingBox.move(point.x - entity.x, point.y - entity.y, point.z - entity.z).deflate(TOUCH)
            return overlaps(entity, box) { _, state -> isTrunkBlock(state) || state.`is`(BlockTags.LEAVES) }
        }

        /** A sapling or a log, so that the net of CD-ASG-25 runs for the change of a tree target and not for a crop's age. */
        private fun isTree(state: BlockState): Boolean = state.`is`(BlockTags.SAPLINGS) || state.`is`(BlockTags.LOGS)

        /**
         * CD-ASG-25: a worker whose body overlaps a block goes to the nearest cell of [Unstick.reachableCells] with a sturdy floor, no
         * fluid, a walkable path type, room for its body out of every sapling's column, inside [area], and on land that [mayUse]
         * allows. It stays when no cell passes. The fill passes natural leaves and the changed tree's [trunk] only ([TreeWay]).
         */
        private fun free(
            entity: PokemonEntity,
            spot: BlockPos?,
            area: WorkArea,
            trunk: Lazy<Set<BlockPos>>,
            mayUse: (BlockPos) -> Boolean,
        ) {
            val level = entity.level()
            if (level.noBlockCollision(entity, entity.boundingBox.deflate(TOUCH))) return
            val passable = { pos: BlockPos ->
                level.isLoaded(pos) &&
                    level.getBlockState(pos).let {
                        TreeWay.passes(it.getCollisionShape(level, pos).isEmpty, isNaturalLeaf(it)) {
                            isTrunkBlock(it) && pos in trunk.value
                        }
                    }
            }
            val width = entity.bbWidth.toDouble()
            val height = entity.bbHeight.toDouble()
            val isSapling = { x: Int, y: Int, z: Int -> level.getBlockState(BlockPos(x, y, z)).`is`(BlockTags.SAPLINGS) }
            var asks = 0
            for (cell in Unstick.reachableCells(entity.blockPosition(), spot, passable)) {
                if (!area.contains(cell)) continue
                val below = cell.below()
                if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) continue
                if (!level.getFluidState(cell).isEmpty) continue
                val type = WalkNodeEvaluator.getPathTypeStatic(entity, cell)
                if (!Unstick.isOpenFoot(type)) continue
                val x = cell.x + 0.5
                val z = cell.z + 0.5
                if (!fits(entity, x, cell.y.toDouble(), z) || !GrowthColumns.clearOfSaplings(cell, width, height, isSapling)) continue
                // CD-LND-12: at most MAX_CLAIM_ASKS checks of the claim mods for one worker.
                if (asks++ >= MAX_CLAIM_ASKS) return
                if (!mayUse(cell)) continue
                entity.navigation.stop()
                entity.teleportTo(x, cell.y.toDouble(), z)
                entity.deltaMovement = Vec3.ZERO
                return
            }
        }

        private fun fits(entity: PokemonEntity, x: Double, y: Double, z: Double): Boolean =
            entity.level().noBlockCollision(entity, entity.boundingBox.move(x - entity.x, y - entity.y, z - entity.z).deflate(TOUCH))

        /** Whether the body of [entity] fits on [spot]'s center, where [snap] puts it, as the spot rule measures it ([WorkerBody]). */
        private fun centerFits(entity: PokemonEntity, spot: BlockPos): Boolean =
            entity.level().noBlockCollision(entity, WorkerBody.boxes(spot, entity.bbWidth.toDouble(), entity.bbHeight.toDouble()).first())

        /** Primary first, which [RoleSelection.best] reads between the roles of one handler. */
        private fun typesOf(pokemon: Pokemon): Set<String> = pokemon.types.mapTo(LinkedHashSet()) { it.showdownId }

        private fun hasFairyType(pokemon: Pokemon): Boolean = pokemon.types.any { it == ElementalTypes.FAIRY }

        private fun hasDarkType(pokemon: Pokemon): Boolean = pokemon.types.any { it == ElementalTypes.DARK }

        /**
         * On arrival: the spot center, at rest, facing the target. The Brain's work activity holds it from the next tick. A spot where the
         * body no longer fits, because a tree grew there since the claim, gets no snap, and the result is false (CD-ASG-25).
         */
        private fun snap(entity: PokemonEntity, spot: BlockPos, lookAt: Vec3): Boolean {
            if (!centerFits(entity, spot)) return false
            entity.navigation.stop()
            entity.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
            entity.teleportTo(spot.x + 0.5, spot.y.toDouble(), spot.z + 0.5)
            entity.deltaMovement = Vec3.ZERO
            BrainBridge.face(entity, lookAt)
            return true
        }

        /** On reaching a bed: the sleep point, at rest. The Brain's work activity holds it there from the next tick (CD-BED-10). */
        private fun lieDown(entity: PokemonEntity, point: Vec3) {
            entity.navigation.stop()
            entity.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
            entity.teleportTo(point.x, point.y, point.z)
            entity.deltaMovement = Vec3.ZERO
        }
    }
}

/** A role whose binding names one target and whose claims share no target, which alone may switch (CD-LSH-33). */
internal fun isSingle(role: ActiveRole): Boolean = role.handler.bindingShape == BindingShape.SINGLE && !role.handler.sharesTargets

/**
 * CD-LSH-33: the switch roles of [own] on [target] among the enabled roles of [roleSet] for [types], [own] first. A handler that throws
 * counts as no match. A worker with no binding ([bound] false) gets auto-assign roles only (CD-ASG-22). A switch role's handler must
 * not refuse a bind on the cell and must hold it valid (CD-LSH-31). [cache] keeps the answer until the block at [target], the role set,
 * [own] or [types] change, or for `SwitchCache.LIFETIME_TICKS` from [now], because the Lumberjack's checks can search a whole tree.
 *
 * CD-LSH-35: null, unknown and never cached, while a cell next to [target] or a cell of a switch role's tree search is in a chunk that
 * is not loaded.
 */
internal fun switchRolesOn(
    level: ServerLevel,
    target: BlockPos,
    own: ActiveRole,
    roleSet: RoleSet,
    types: Collection<String>,
    bound: Boolean,
    cache: SwitchCache<List<ActiveRole>>? = null,
    now: Long = 0,
): List<ActiveRole>? {
    val state = level.getBlockState(target)
    val compute: () -> List<ActiveRole>? = compute@{
        if (!neighboursLoaded(level, target)) return@compute null
        val enabled = roleSet.forTypes(types)
        val byId = enabled.associateBy { it.definition.id } + (own.definition.id to own)
        val blockEntity by lazy { level.getBlockEntity(target) }
        val handlerOf = { def: RoleDefinition -> byId.getValue(def.id).handler }
        val roles = RoleSwitch.rolesOrUnknown(
            own.definition,
            enabled.map { it.definition },
            bound,
            single = { def -> isSingle(byId.getValue(def.id)) },
            matches = { def -> handlerOf(def).let { h -> guarded(h, false) { h.matches(level, target, state, blockEntity) } } },
            resolvesToItself = { def -> handlerOf(def).let { h -> guarded(h, false) { h.resolvesToItself(level, target) } } },
            accepts = { def -> handlerOf(def).let { h -> guarded<Boolean?>(h, false) { h.switchAnswer(level, target, def) } } },
        )
        roles?.map { byId.getValue(it.id) }
    }
    if (cache == null) return compute()
    return cache.get(listOf(target, own.definition.id, bound, state, roleSet, types.toList()), now, compute)
}

/** CD-LSH-35: the cells of [pos]'s column and of the 8 columns around it are loaded, which the switch checks of a cell read. */
internal fun neighboursLoaded(level: ServerLevel, pos: BlockPos): Boolean =
    (-1..1).all { dx -> (-1..1).all { dz -> level.isLoaded(pos.offset(dx, 0, dz)) } }

/**
 * A handler that throws is turned off with one warning (CD-PERF-05). A `LinkageError` counts too, as when a later version of an
 * optional mod renames a member that a compat handler calls (CD-OPT-10). The next tick releases the claims of its roles.
 */
internal inline fun <T> guarded(handler: TargetHandler, fallback: T, block: () -> T): T = try {
    block()
} catch (e: Exception) {
    RoleRegistry.disableHandler(handler.id, e.toString())
    fallback
} catch (e: LinkageError) {
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
