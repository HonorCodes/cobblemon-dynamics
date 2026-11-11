package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.Feedback
import codes.honor.cobblemondynamics.OnceLogger
import codes.honor.cobblemondynamics.ai.BrainBridge
import codes.honor.cobblemondynamics.ai.WorkOrder
import codes.honor.cobblemondynamics.bed.BedBlock
import codes.honor.cobblemondynamics.bed.BedBlockEntity
import codes.honor.cobblemondynamics.bed.BedCandidate
import codes.honor.cobblemondynamics.bed.BedSelector
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.claim.ClaimRegistry
import codes.honor.cobblemondynamics.claim.ClaimResult
import codes.honor.cobblemondynamics.claim.TargetKey
import codes.honor.cobblemondynamics.compat.LandClaims
import codes.honor.cobblemondynamics.compat.WorkerEvents
import codes.honor.cobblemondynamics.config.EngineSettings
import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.fx.EffectPlayer
import codes.honor.cobblemondynamics.handler.ClaimMemo
import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.heal.AllyView
import codes.honor.cobblemondynamics.heal.HealTargets
import codes.honor.cobblemondynamics.heal.HealTransaction
import codes.honor.cobblemondynamics.leash.Binding
import codes.honor.cobblemondynamics.leash.BindingStore
import codes.honor.cobblemondynamics.pasture.PastureAdapter
import codes.honor.cobblemondynamics.pasture.PastureKey
import codes.honor.cobblemondynamics.pasture.TetheredPokemon
import codes.honor.cobblemondynamics.registry.DynamicsAttachments
import codes.honor.cobblemondynamics.role.ActiveRole
import codes.honor.cobblemondynamics.role.RoleDefinition
import codes.honor.cobblemondynamics.role.RoleRegistry
import codes.honor.cobblemondynamics.role.RoleSelection
import codes.honor.cobblemondynamics.role.RoleSet
import codes.honor.cobblemondynamics.stamina.FlushRule
import codes.honor.cobblemondynamics.stamina.RecoveryRule
import codes.honor.cobblemondynamics.stamina.StaminaStore
import codes.honor.cobblemondynamics.stamina.StaminaView
import codes.honor.cobblemondynamics.stamina.ViewSync
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
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

    fun worker(pokemonId: UUID): Worker? = slots[pokemonId]?.worker

    fun entityOf(pokemonId: UUID): PokemonEntity? = slots[pokemonId]?.entity?.takeUnless { it.isRemoved }

    fun pokemonOf(pokemonId: UUID): Pokemon? = slots[pokemonId]?.pokemon

    /** The recovery rule that the engine applies to a worker, from its types and its binding at the last poll. */
    fun ruleOf(pokemonId: UUID): RecoveryRule? = slots[pokemonId]?.rule

    /** The role of a worker's work claim, while it holds one. */
    fun roleOf(pokemonId: UUID): ActiveRole? = slots[pokemonId]?.role

    /**
     * True while [entity] is the entity of a worker that the engine directs: any state but Idle, in which the worker walks to or holds
     * a place that the engine gave it. Two such workers do not push each other (`WorkerPush`).
     */
    fun isDirected(entity: PokemonEntity): Boolean {
        val slot = slots[entity.pokemon.uuid] ?: return false
        return slot.entity === entity && slot.worker.status != WorkerStatus.IDLE
    }

    fun isQuickWorker(pokemonId: UUID): Boolean = pokemonId in orders

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
        for (runtime in runtimesOf(dimension)) for (slot in runtime.slots.values.toList()) direct(t, slot)
        for (order in quick) if (orders[order.slot.worker.pokemonId] === order) direct(t, order.slot)
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
        val holder = if (shared) null else claims.holderOf(TargetKey.block(t.dimension, binding.target))
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
        val pick = QuickWorkRules.pick(fitting.map { it.definition }, types.toList(), hasFairyType(pokemon)) { def ->
            val handler = byId.getValue(def.id).handler
            QuickWorkRules.mayOrder(def, handler.scanKind != ScanKind.NONE, handler.bindingShape)
        }
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
        val present = HashSet<UUID>()
        for (tethered in PastureAdapter.tethered(be)) {
            val pokemon = tethered.pokemon ?: continue
            val entity = tethered.entity ?: continue
            if (!canWork(t.level, tethered, pokemon, entity)) continue
            present += tethered.pokemonId
            val slot = runtime.slots[tethered.pokemonId]
            if (slot != null) {
                val old = slot.entity
                slot.entity = entity
                slot.pokemon = pokemon
                if (old !== entity) moveClaim(t, slot, old)
            } else if (tethered.pokemonId !in slots) {
                val worker = Worker(tethered.pokemonId, runtime.key)
                // CD-STA-05, CD-STA-11: a new worker starts at 100%, and a known one recovers for its time away.
                worker.stamina = StaminaStore.load(pokemon, t.now, t.settings)
                worker.staminaUntil = t.now
                val joined = WorkerSlot(worker, entity, pokemon)
                runtime.slots[tethered.pokemonId] = joined
                slots[tethered.pokemonId] = joined
            }
        }
        for (slot in runtime.slots.values.toList()) if (slot.worker.pokemonId !in present) drop(t, runtime, slot)
        val taken = takenSpots(t)
        for (slot in runtime.slots.values.toList()) {
            // A binding counts only for the pasture that it names. LeashResets clears one that names another.
            slot.binding = BindingStore.read(slot.pokemon)?.takeIf { it.dimension == t.dimension && it.pasture == runtime.pos }
            slot.worker.leashed = slot.binding != null
            slot.rule = RecoveryRule.of(hasFairyType(slot.pokemon), slot.binding != null)
            claimBound(t, runtime, slot, taken)
            // CD-CMP-01: the Cobbleworkers shim marks each worker within one poll of Work Mode turning on.
            WorkerEvents.polled(slot.pokemon)
        }
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

    /** A leashed worker claims its bound target and no other (CD-ASG-20). A Tired worker waits until it has rested (CD-STA-14). */
    private fun claimBound(t: Tick, runtime: PastureRuntime, slot: WorkerSlot, taken: MutableSet<Long>) {
        val binding = slot.binding ?: return
        val worker = slot.worker
        val key = TargetKey.block(t.dimension, binding.target)
        val current = claims.claimOf(worker.pokemonId)
        if (current?.kind == ClaimKind.WORK && current.key != key) release(t, slot)
        if (claims.claimOf(worker.pokemonId) != null || worker.tired || worker.isSkipped(key, t.now)) return
        if (!BrainBridge.canHoldOrder(slot.entity)) return
        val role = t.roles.get(binding.roleId)?.takeIf { RoleSelection.isEligible(it.definition, typesOf(slot.pokemon)) } ?: return
        val valid = t.level.isLoaded(binding.target) && guarded(role.handler, false) { role.handler.stillValid(t.level, binding.target) }
        if (valid && claim(t, runtime, slot, binding.target, role, taken, moving = false) == Attempt.NO_SPOT) {
            cannotReach(t, slot, binding.target)
        }
    }

    /** The work spots that workers of this level hold, as `BlockPos.asLong()`, Quick Workers' included. */
    private fun takenSpots(t: Tick): MutableSet<Long> =
        slots.values.filter { dimensionOf(it) == t.dimension }.mapNotNullTo(HashSet()) { it.worker.spot?.asLong() }

    /** CD-LSH-21: the owner of a leashed worker learns why its machine waits. */
    private fun cannotReach(t: Tick, slot: WorkerSlot, target: BlockPos) {
        val owner = slot.pokemon.getOwnerUUID() ?: return
        val name = t.level.getBlockState(target).block.name
        Feedback.actionBar(owner, server, Component.translatable(CANNOT_REACH, slot.pokemon.getDisplayName(), name))
    }

    /** Owned, not fainted, no battle clone, in this level, and tethered by its owner. */
    private fun canWork(level: ServerLevel, tethered: TetheredPokemon, pokemon: Pokemon, entity: PokemonEntity): Boolean =
        pokemon.isPlayerOwned() &&
            !pokemon.isFainted() &&
            !pokemon.isBattleClone() &&
            !entity.isRemoved &&
            entity.level() === level &&
            tethered.ownerId == pokemon.getOwnerUUID()

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

    /** After a reload, a worker keeps a claim only while its role still takes it. */
    private fun mayKeep(slot: WorkerSlot, role: RoleDefinition): Boolean =
        RoleSelection.isEligible(role, typesOf(slot.pokemon)) && (slot.worker.leashed || role.autoAssign)

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
        assign(t, runtime, pass.found)
        assignHeals(t, runtime)
    }

    /**
     * After each scan pass, every free healer of the pasture picks an ally of the same pasture and takes a heal claim on it
     * (CD-HEAL-01). A healer is a Fairy-type with no machine binding, so it follows the healer rule (CD-HEAL-14).
     */
    private fun assignHeals(t: Tick, runtime: PastureRuntime) {
        val allies = runtime.slots.values.filterNot { it.entity.isRemoved }.map(::allyView)
        for (slot in runtime.slots.values) {
            val worker = slot.worker
            if (slot.rule != RecoveryRule.HEALER || worker.status != WorkerStatus.IDLE || claims.claimOf(worker.pokemonId) != null) continue
            if (!HealTargets.canStart(worker.stamina.fraction, worker.tired, t.settings) || !BrainBridge.canHoldOrder(slot.entity)) continue
            val isFree: (UUID) -> Boolean = {
                val key = TargetKey.Entity(it)
                claims.isFree(key, worker.pokemonId) && !worker.isSkipped(key, t.now)
            }
            val ally = HealTargets.pick(worker.pokemonId, slot.entity.position(), allies, isFree, t.settings) ?: continue
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
        val types = runtime.slots.values.filter(::mayAutoAssign).map { typesOf(it.pokemon) }
        if (types.isEmpty()) return emptyList()
        return t.roles.active.filter { role ->
            role.definition.autoAssign &&
                role.handler.scanKind != ScanKind.NONE &&
                types.any { RoleSelection.isEligible(role.definition, it) }
        }
    }

    private fun mayAutoAssign(slot: WorkerSlot): Boolean =
        !slot.worker.leashed && !slot.worker.tired && !hasFairyType(slot.pokemon) && BrainBridge.canHoldOrder(slot.entity)

    private fun assign(t: Tick, runtime: PastureRuntime, found: List<FoundTarget>) {
        if (found.isEmpty()) return
        val byKey: Map<TargetKey, FoundTarget> = found.associateBy { TargetKey.block(t.dimension, it.pos) }
        val taken = takenSpots(t)
        for (slot in runtime.slots.values) {
            val worker = slot.worker
            // CD-ASG-20: the poll gives a leashed worker its bound target, and a scan never gives it another.
            if (worker.leashed) continue
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
            claim(t, runtime, slot, target.pos, target.roles.first { it.definition.id == pick.second.id }, taken, moving = current != null)
        }
    }

    private fun candidate(t: Tick, slot: WorkerSlot, types: Set<String>, key: TargetKey, target: FoundTarget): CandidateView {
        val center = target.roles.first().handler.targetCenter(target.pos)
        return CandidateView(key, center, target.roles.map { it.definition }) {
            target.roles.any { role ->
                RoleSelection.isEligible(role.definition, types) &&
                    guarded(role.handler, false) { role.handler.hasDemand(context(t, slot, target.pos, role)) }
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
        val key = TargetKey.block(t.dimension, target)
        val shared = role.handler.sharesTargets
        // CD-CLM-10: a container whose places are all taken refuses before the spot search and its path checks.
        if (shared && !claims.hasPlace(key)) return Attempt.REFUSED
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
        val allowed: (BlockPos) -> Boolean = { role.handler.allowsSpot(level, target, it, height) }
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
        val role = slot.role
        val target = worker.target
        val spot = worker.spot
        if (role == null || target == null || spot == null) {
            release(t, slot)
            return null
        }
        val handler = role.handler
        val ctx = context(t, slot, target, role, slot.memo)
        val arrived = isArrived(slot.entity, spot)
        val before = worker.status
        var event: MachineEvent? = null
        val ok = guarded(handler, false) {
            event = t.machine.onTick(worker, t.now, arrived, { handler.hasDemand(ctx) }, claim.key)
            when (val e = event) {
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
                    e.skip?.let { worker.skipUntil[it] = e.until }
                    clear(t, slot)
                }

                MachineEvent.MissedArrival -> cannotReach(t, slot, target)

                else -> {}
            }
            if (worker.status == WorkerStatus.WORKING) handler.applyWork(ctx)
            true
        }
        if (!ok) release(t, slot, callHandler = false)
        return event
    }

    /**
     * Work Stamina drains on each tick that the handler worked and recovers on every other tick, at the worker's rule (CD-STA-06,
     * CD-STA-07, CD-HEAL-12, CD-HEAL-13), times the bed's factor only while the worker lies on it (CD-BED-04). A worker that this
     * makes Tired stops in the same tick (CD-STA-13), and one that this fills gets up in the same tick (CD-BED-12). Then Work Stamina
     * is flushed when due, and the view follows (CD-STA-15).
     */
    private fun tickStamina(t: Tick, site: WorkSite, slot: WorkerSlot) {
        val worker = slot.worker
        val ticks = t.now + 1 - worker.staminaUntil
        if (ticks > 0) {
            val level = slot.pokemon.level
            if (worker.status == WorkerStatus.WORKING) {
                worker.stamina.work(ticks, level, t.settings)
            } else {
                val bedTier = if (worker.status == WorkerStatus.SLEEPING) worker.bed?.tier else null
                worker.stamina.rest(ticks, level, slot.rule, bedTier, t.settings)
            }
            worker.staminaUntil = t.now + 1
        }
        // A worker that joins Tired, after a restart for example, takes a bed on its first tick too (CD-BED-14).
        if (worker.tired && !worker.status.isResting) {
            when (site) {
                is PastureRuntime -> goToRest(t, site, slot)

                // CD-QWK-17: no pasture holds a Quick Worker, so it takes no bed. The end flushes its Work Stamina.
                is QuickRuntime -> return endOrder(t, site, QuickEnd.TIRED)
            }
        } else if (!worker.tired && worker.status.isResting && site is PastureRuntime) {
            wake(t, site, slot)
        }
        if (FlushRule.isDue(slot.flushedStatus, worker.status, slot.flushedAt, t.now, t.settings)) flush(slot, t.now)
        syncView(slot)
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
        val beds = t.world.blockEntitiesIn(runtime.area(t.settings)).mapNotNull { (pos, blockEntity) ->
            val bed = blockEntity as? BedBlockEntity ?: return@mapNotNull null
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
            MachineEvent.FellAsleep -> lieDown(entity, bed.center)

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
            HealTransaction.complete(worker.stamina, ally.worker.stamina, t.settings)
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
    private fun direct(t: Tick, slot: WorkerSlot) {
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
        val center = guarded(role.handler, null) { role.handler.targetCenter(target) }
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
        EffectPlayer.tickWorking(t.level, entity, focus, role.definition, worker.statusSince, t.now)
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
            targetFree = claims.isFree(TargetKey.block(t.dimension, canonical), pokemon.uuid),
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
        unclaim(t, slot, slot.entity)
        slot.worker.target = null
        slot.worker.spot = null
        slot.role = null
        slot.memo = ClaimMemo()
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
            // Only the Magnet reads it, and the Magnet is leash only, so no Quick Worker needs it.
            pasture = slot.worker.pasture?.let { BlockPos.of(it.pos) },
            memo = memo,
        )
    }

    /** CD-CLM-11: only a binding of an exclusive role reserves its target. */
    private fun reservedFor(key: TargetKey): UUID? {
        val block = key as? TargetKey.Block ?: return null
        val level = server.getLevel(ResourceKey.create(Registries.DIMENSION, block.dimension)) ?: return null
        val index = level.getExistingDataOrNull(DynamicsAttachments.LEASH_INDEX) ?: return null
        return index.exclusiveAt(BlockPos.of(block.pos), RoleRegistry::sharesTargets)?.pokemonId
    }

    companion object {
        private const val CANNOT_REACH = "cobblemon_dynamics.leash.cannot_reach"

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

        /** Primary first, which [RoleSelection.best] reads between the roles of one handler. */
        private fun typesOf(pokemon: Pokemon): Set<String> = pokemon.types.mapTo(LinkedHashSet()) { it.showdownId }

        private fun hasFairyType(pokemon: Pokemon): Boolean = pokemon.types.any { it == ElementalTypes.FAIRY }

        /** On arrival: the spot center, at rest, facing the target. The Brain's work activity holds it from the next tick. */
        private fun snap(entity: PokemonEntity, spot: BlockPos, lookAt: Vec3) {
            entity.navigation.stop()
            entity.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
            entity.teleportTo(spot.x + 0.5, spot.y.toDouble(), spot.z + 0.5)
            entity.deltaMovement = Vec3.ZERO
            BrainBridge.face(entity, lookAt)
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

/** A handler that throws is turned off with one warning (CD-PERF-05). The next tick releases the claims of its roles. */
internal inline fun <T> guarded(handler: TargetHandler, fallback: T, block: () -> T): T = try {
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
