package codes.honor.cobblemondynamics.leash

import codes.honor.cobblemondynamics.Feedback
import codes.honor.cobblemondynamics.claim.LeashIndex
import codes.honor.cobblemondynamics.engine.EngineTiming
import codes.honor.cobblemondynamics.engine.RoleSwitch
import codes.honor.cobblemondynamics.engine.SwitchCache
import codes.honor.cobblemondynamics.engine.WorkEngine
import codes.honor.cobblemondynamics.engine.guarded
import codes.honor.cobblemondynamics.engine.neighboursLoaded
import codes.honor.cobblemondynamics.engine.switchRolesOn
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.farming.CultivatorHandler
import codes.honor.cobblemondynamics.handler.farming.SaplingRules
import codes.honor.cobblemondynamics.handler.tree.LumberjackHandler
import codes.honor.cobblemondynamics.handler.tree.TreeRules
import codes.honor.cobblemondynamics.pasture.PastureAdapter
import codes.honor.cobblemondynamics.registry.DynamicsAttachments
import codes.honor.cobblemondynamics.role.RoleRegistry
import codes.honor.cobblemondynamics.role.RoleSelection
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.tick.LevelTickEvent
import net.neoforged.neoforge.server.ServerLifecycleHooks
import java.util.UUID

/**
 * Clears a binding whose Pokémon, target or destination is gone, and tells the binder why (CD-LSH-13). Cobblemon fires no pasture
 * events, so the index is checked every 10 ticks. A binding whose pasture and target are both unloaded waits for them (CD-LSH-09).
 */
object LeashResets {
    const val TARGET_GONE = "target_gone"
    const val DESTINATION_GONE = "destination_gone"
    const val LEFT_PASTURE = "left_pasture"
    const val OTHER_PASTURE = "other_pasture"
    const val RELEASED = "released"
    const val TRADED = "traded"
    const val NO_ROLE = "no_role"
    const val PASTURE_GONE = "pasture_gone"
    const val CLEARED_BY_OPERATOR = "cleared_by_operator"

    /** CD-LND-03: the placer of the binding's pasture may not use its target, or its Telekinesis destination, in a land claim. */
    const val LAND_CLAIM = "land_claim"
    const val LAND_CLAIM_DESTINATION = "land_claim_destination"

    private const val CHECK_TICKS = 10L

    /** CD-LSH-31: the switch answer of each index entry by Pokémon, until the block at its target changes or the answer expires. */
    private val switchHolds = HashMap<UUID, SwitchCache<Boolean>>()

    /** CD-LSH-36: the Pokémon whose binding this server run has checked for the move of CD-CUL-13 already. */
    private val migrated = HashSet<UUID>()

    /** CD-LSH-35: the first tick at which the owner's PC and party held no Pokémon of an entry whose pasture is not loaded. */
    private val missing = HashMap<UUID, Long>()

    /** CD-LSH-35: how long an entry whose Pokémon is found nowhere, with its pasture not loaded, keeps its reservation. */
    const val MISSING_TICKS = 6_000L
    private const val LOST = "cobblemon_dynamics.binding.lost"
    private const val REASON = "cobblemon_dynamics.binding.reason"

    fun register() {
        CobblemonEvents.POKEMON_RELEASED_EVENT_POST.subscribe(Priority.NORMAL) { clear(it.player.server, it.pokemon, RELEASED) }
        CobblemonEvents.POKEMON_GAINED.subscribe(Priority.NORMAL) { event ->
            ServerLifecycleHooks.getCurrentServer()?.let { onGained(it, event.playerId, event.pokemon) }
        }
        CobblemonEvents.EVOLUTION_COMPLETE.subscribe(Priority.NORMAL) { event ->
            ServerLifecycleHooks.getCurrentServer()?.let { recheckRole(it, event.pokemon) }
        }
        NeoForge.EVENT_BUS.addListener<LevelTickEvent.Post> { event ->
            val level = event.level as? ServerLevel ?: return@addListener
            if (level.gameTime % CHECK_TICKS == 0L) EngineTiming.time { checkIndex(level) }
        }
    }

    /**
     * Clears [pokemon]'s binding and its index entry, and tells the engine. [reason] names the cause in the binder's chat message.
     * The owner's own sneak-click passes null and gets no chat message. Returns the binding that was cleared.
     */
    fun clear(server: MinecraftServer, pokemon: Pokemon, reason: String?): Binding? {
        val binding = BindingStore.read(pokemon) ?: return null
        BindingStore.clear(pokemon)
        migrated.remove(pokemon.uuid)
        levelOf(server, binding.dimension)?.let { unindex(it, binding.target, pokemon.uuid) }
        WorkEngine.get()?.onUnbound(pokemon.uuid)
        if (reason != null) {
            val message = Component.translatable(LOST, pokemon.getDisplayName(), Component.translatable("$REASON.$reason"))
            Feedback.chat(binding.binder, server, message)
        }
        return binding
    }

    /** One binding that [clearAt] removed: its key in the index, the index entry, and its Pokémon when the owner's PC or party holds it. */
    data class Unbound(val target: BlockPos, val ref: LeashIndex.Ref, val pokemon: Pokemon?)

    /**
     * CD-UI-17: clears every binding whose target, or Telekinesis source, is the block at [pos] in [level], as the Work Leash finds that
     * block: [pos] itself and the canonical block of each role whose handler matches it, such as the centre of a large water wheel. A
     * container of logistics roles can hold several. Each binder gets the chat message with the reason [CLEARED_BY_OPERATOR]. An entry
     * whose Pokémon is gone loses only its reservation.
     */
    fun clearAt(level: ServerLevel, pos: BlockPos): List<Unbound> {
        val index = level.getExistingDataOrNull(DynamicsAttachments.LEASH_INDEX) ?: return emptyList()
        val dimension = level.dimension().location()
        return keysAt(level, pos).flatMap { key ->
            index.at(key).map { ref ->
                val pokemon = find(level.server, ref)
                val binding = pokemon?.let(BindingStore::read)
                if (pokemon != null && binding?.dimension == dimension && binding.target == key) {
                    clear(level.server, pokemon, CLEARED_BY_OPERATOR)
                } else {
                    unindex(level, key, ref.pokemonId)
                }
                Unbound(key, ref, pokemon)
            }
        }
    }

    /** [pos] and the canonical block of each role whose handler matches it: the index keys that a binding on that block can have. */
    private fun keysAt(level: ServerLevel, pos: BlockPos): Set<BlockPos> {
        val keys = linkedSetOf(pos.immutable())
        if (!level.isLoaded(pos)) return keys
        val state = level.getBlockState(pos)
        val blockEntity = level.getBlockEntity(pos)
        for (role in RoleRegistry.current.active) {
            val handler = role.handler
            val matches = guarded(handler, false) { handler.matches(level, pos, state, blockEntity) }
            if (matches) guarded(handler, null) { handler.canonical(level, pos) }?.let { keys += it.immutable() }
        }
        return keys
    }

    /** CD-LSH-26: binding data never travels to a new owner. */
    fun onGained(server: MinecraftServer, playerId: UUID, pokemon: Pokemon) {
        val binding = BindingStore.read(pokemon) ?: return
        if (binding.binder != playerId) clear(server, pokemon, TRADED)
    }

    /**
     * CD-LSH-14: after a change of form, the binding stays while a role of the same handler fits the new types, and takes the best
     * of them. A bound role that a data pack set to `leash` false counts as a role that no longer fits (CD-PCK-04). Returns false when the binding is gone. A role that the config or a failed handler turned off keeps its bindings.
     */
    fun recheckRole(server: MinecraftServer, pokemon: Pokemon): Boolean {
        val binding = BindingStore.read(pokemon) ?: return false
        val roles = RoleRegistry.current
        val bound = roles.get(binding.roleId) ?: return true
        val types = pokemon.types.map { it.showdownId }
        if (RoleSelection.bindingFits(bound.definition, types)) return true
        val next = RoleSelection.best(roles.forLeash(types).map { it.definition }.filter { it.handler == bound.definition.handler }, types)
        if (next == null) {
            clear(server, pokemon, NO_ROLE)
            return false
        }
        BindingStore.write(pokemon, binding.copy(roleId = next.id))
        val level = levelOf(server, binding.dimension) ?: return true
        val index = level.getData(DynamicsAttachments.LEASH_INDEX)
        index.at(binding.target).firstOrNull { it.pokemonId == pokemon.uuid }?.let { index.put(binding.target, it.copy(roleId = next.id)) }
        level.setData(DynamicsAttachments.LEASH_INDEX, index)
        return true
    }

    /** Removes the index entry of [pokemonId] at [target]. */
    fun unindex(level: ServerLevel, target: BlockPos, pokemonId: UUID) {
        switchHolds.remove(pokemonId)
        missing.remove(pokemonId)
        val index = level.getExistingDataOrNull(DynamicsAttachments.LEASH_INDEX) ?: return
        if (index.remove(target, pokemonId)) level.setData(DynamicsAttachments.LEASH_INDEX, index)
    }

    private fun reindex(level: ServerLevel, target: BlockPos, ref: LeashIndex.Ref) {
        val index = level.getData(DynamicsAttachments.LEASH_INDEX)
        index.put(target, ref)
        level.setData(DynamicsAttachments.LEASH_INDEX, index)
    }

    /** Forgets the switch answers and the moves of CD-CUL-13 when the server stops. */
    fun reset() {
        switchHolds.clear()
        migrated.clear()
        missing.clear()
    }

    fun levelOf(server: MinecraftServer, dimension: ResourceLocation): ServerLevel? =
        server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension))

    /** CD-LSH-08, CD-LSH-24, CD-LSH-27, CD-WMD-07: each entry whose pasture or target is loaded. */
    fun checkIndex(level: ServerLevel) {
        val index = level.getExistingDataOrNull(DynamicsAttachments.LEASH_INDEX) ?: return
        for ((target, ref) in index.all()) check(level, target, ref)
    }

    private fun check(level: ServerLevel, target: BlockPos, ref: LeashIndex.Ref) {
        var checked: Pokemon? = null
        if (level.isLoaded(ref.pasture)) {
            val be = PastureAdapter.pastureAt(level, ref.pasture) ?: return lost(level, target, ref, PASTURE_GONE)
            val tethered = PastureAdapter.tetheredPokemon(be, ref.pokemonId)
            val pokemon = tethered?.pokemon
            if (pokemon == null) {
                // Recalled to the PC or the party, or tethered to another pasture. A released Pokémon is found nowhere.
                val found = find(level.server, ref)
                val reason = if (tethered == null && found != null && PastureAdapter.isTethered(found)) OTHER_PASTURE else LEFT_PASTURE
                return lost(level, target, ref, reason, found)
            }
            val bound = BindingStore.read(pokemon)
            if (bound == null || bound.target != target) return unindex(level, target, ref.pokemonId)
            // An entry from before the index named destinations gets its destination here, so that its place counts (CD-LSH-29).
            if (bound.destination != ref.destination) reindex(level, target, ref.copy(destination = bound.destination))
            if (!recheckRole(level.server, pokemon)) return
            checked = pokemon
            // CD-TLK-11: a two-target binding also ends when its destination is broken or replaced.
            val destination = BindingStore.read(pokemon)?.destination
            val handler = RoleRegistry.current.get(ref.roleId)?.handler
            if (destination != null &&
                handler != null &&
                level.isLoaded(destination) &&
                !guarded(handler, true) { handler.stillValid(level, destination) }
            ) {
                return lost(level, target, ref, DESTINATION_GONE, pokemon)
            }
        }
        if (!level.isLoaded(target)) return
        val roles = RoleRegistry.current
        val bound = roles.get(ref.roleId) ?: return
        val handler = bound.handler
        val state = level.getBlockState(target)
        // CD-CUL-13, CD-LSH-36: 1.2.0 bound a Cultivator to the clicked sapling of a 2 × 2, and 1.2.1 names the square's corner. The move
        // runs once for each binding in a server run, as a migration, and not on each check.
        if (checked != null && ref.pokemonId !in migrated) {
            val moved = migrate(level, target, ref, checked, handler, state) ?: return
            migrated += ref.pokemonId
            if (moved) return
        }
        // CD-CRP-09: a farming binding keeps the empty cell of a harvested crop.
        if (guarded(handler, true) { handler.bindingHolds(level, target) }) return
        // CD-LSH-37: a log of a 2 × 2 trunk whose base is another cell of the square moves its binding to the base.
        if ((handler === LumberjackHandler || handler === CultivatorHandler) && state.`is`(BlockTags.LOGS)) {
            if (!neighboursLoaded(level, target)) return
            val base = guarded(LumberjackHandler, target) { LumberjackHandler.canonical(level, target) }
            if (base != target && TreeRules.inSquareOf(target, base)) return moveToBase(level, target, ref, checked, base)
        }
        // CD-LSH-31: a switch role that finds a target of its own on the cell, and would fell or work it, keeps the binding, as the
        // Lumberjack on the oak that grew from a Cultivator's sapling. The Pokémon is the one that this check read, or the one that its
        // owner's PC or party holds when its pasture is not loaded. The answer lasts until the block at the target changes, or for
        // `SwitchCache.LIFETIME_TICKS`.
        // CD-LSH-35: an unknown answer, from a chunk that is not loaded or a Pokémon found nowhere, keeps the binding, is not cached,
        // and is asked again at a later check.
        // CD-LSH-35: an entry whose Pokémon's binding names another target, or whose Pokémon stays missing, goes.
        var stale = false
        val key = listOf(target, ref.roleId, state, roles, checked?.types?.map { it.showdownId })
        val holds = switchHolds.getOrPut(ref.pokemonId) { SwitchCache() }.get(key, level.gameTime) compute@{
            val pokemon = checked ?: found(level, target, ref) { stale = true } ?: return@compute null
            val types = pokemon.types.map { it.showdownId }
            RoleSwitch.holdsOrUnknown(ownHolds = false) {
                switchRolesOn(level, target, bound, roles, types, bound = true)?.map { it.definition }
            }
        }
        if (stale) return unindex(level, target, ref.pokemonId)
        if (holds == false) lost(level, target, ref, TARGET_GONE, checked ?: find(level.server, ref))
    }

    /**
     * CD-LSH-35: the Pokémon of [ref] from its owner's PC or party, while its pasture is not loaded, when its binding still names [target]
     * in [level]. A binding that names another place, or a Pokémon that stays missing for [MISSING_TICKS], calls [stale]. Null keeps the
     * entry for now.
     */
    private fun found(level: ServerLevel, target: BlockPos, ref: LeashIndex.Ref, stale: () -> Unit): Pokemon? {
        val pokemon = find(level.server, ref)
        if (pokemon == null) {
            val since = missing.getOrPut(ref.pokemonId) { level.gameTime }
            if (level.gameTime - since >= MISSING_TICKS) stale()
            return null
        }
        missing.remove(ref.pokemonId)
        val binding = BindingStore.read(pokemon)
        if (binding == null || binding.dimension != level.dimension().location() || binding.target != target) {
            stale()
            return null
        }
        return pokemon
    }

    /**
     * CD-LSH-37: moves the binding of [ref] from [target] to [base], the trunk base in its 2 × 2, unless an exclusive binding of another
     * Pokémon in the same lane reserves [base], when the binding clears and tells its binder. With no Pokémon read or found, it waits.
     */
    private fun moveToBase(level: ServerLevel, target: BlockPos, ref: LeashIndex.Ref, checked: Pokemon?, base: BlockPos) {
        val pokemon = checked ?: find(level.server, ref) ?: return
        if (BindingStore.read(pokemon)?.target != target) return unindex(level, target, ref.pokemonId)
        val lane = RoleRegistry.laneOf(ref.roleId)
        val index = level.getExistingDataOrNull(DynamicsAttachments.LEASH_INDEX)
        val holder = index?.exclusiveAt(base, lane, RoleRegistry::sharesTargets, RoleRegistry::laneOf)
        if (holder != null && holder.pokemonId != ref.pokemonId) return lost(level, target, ref, TARGET_GONE, pokemon)
        moveTarget(level, target, ref, pokemon, base)
    }

    /**
     * CD-CUL-13, CD-LSH-36: moves a Cultivator binding on a sapling to the corner that the handler names now, unless an exclusive binding
     * of another Pokémon in the same lane reserves a cell of that corner's square. True when it moved, false when it stays, and null, to ask again at a later
     * check, while a cell that the corner reads is in a chunk that is not loaded.
     */
    private fun migrate(
        level: ServerLevel,
        target: BlockPos,
        ref: LeashIndex.Ref,
        pokemon: Pokemon,
        handler: TargetHandler,
        state: BlockState,
    ): Boolean? {
        if (handler !== CultivatorHandler || !state.`is`(BlockTags.SAPLINGS)) return false
        if (!neighboursLoaded(level, target)) return null
        val corner = guarded(handler, target) { handler.canonical(level, target) }
        if (corner == target) return false
        // A binding of another Pokémon on any cell of the corner's square keeps this one where it is, so no two bindings share a square.
        val index = level.getExistingDataOrNull(DynamicsAttachments.LEASH_INDEX)
        val lane = RoleRegistry.laneOf(ref.roleId)
        val taken = SaplingRules.square(corner).any { cell ->
            index?.at(cell).orEmpty().any {
                it.pokemonId != ref.pokemonId && !RoleRegistry.sharesTargets(it.roleId) && RoleRegistry.laneOf(it.roleId) == lane
            }
        }
        if (taken) return false
        moveTarget(level, target, ref, pokemon, corner)
        return true
    }

    /** Moves the binding of [pokemon] and its index entry from [from] to [to], with no message. */
    private fun moveTarget(level: ServerLevel, from: BlockPos, ref: LeashIndex.Ref, pokemon: Pokemon, to: BlockPos) {
        val binding = BindingStore.read(pokemon) ?: return
        BindingStore.write(pokemon, binding.copy(target = to))
        unindex(level, from, ref.pokemonId)
        reindex(level, to, ref)
    }

    private fun lost(
        level: ServerLevel,
        target: BlockPos,
        ref: LeashIndex.Ref,
        reason: String,
        pokemon: Pokemon? = find(level.server, ref),
    ) {
        if (pokemon != null && BindingStore.read(pokemon)?.target == target) {
            clear(level.server, pokemon, reason)
        } else {
            unindex(level, target, ref.pokemonId)
        }
    }

    /** The Pokémon of [ref] in its owner's PC or party. Cobblemon loads the stores of an offline owner. */
    private fun find(server: MinecraftServer, ref: LeashIndex.Ref): Pokemon? {
        val access = server.registryAccess()
        return Cobblemon.storage.getPC(ref.owner, access)[ref.pokemonId] ?: Cobblemon.storage.getParty(ref.owner, access)[ref.pokemonId]
    }
}
