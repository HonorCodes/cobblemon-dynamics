package codes.honor.cobblemondynamics.leash

import codes.honor.cobblemondynamics.Feedback
import codes.honor.cobblemondynamics.claim.LeashIndex
import codes.honor.cobblemondynamics.engine.EngineTiming
import codes.honor.cobblemondynamics.engine.WorkEngine
import codes.honor.cobblemondynamics.engine.guarded
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
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.tick.LevelTickEvent
import net.neoforged.neoforge.server.ServerLifecycleHooks
import java.util.UUID

/**
 * Clears a binding whose Pokémon or target is gone, and tells the binder why (CD-LSH-13). Cobblemon fires no pasture events, so
 * the index is checked every 10 ticks. A binding whose pasture and target are both unloaded waits for them (CD-LSH-09).
 */
object LeashResets {
    const val TARGET_GONE = "target_gone"
    const val LEFT_PASTURE = "left_pasture"
    const val OTHER_PASTURE = "other_pasture"
    const val RELEASED = "released"
    const val TRADED = "traded"
    const val NO_ROLE = "no_role"
    const val PASTURE_GONE = "pasture_gone"

    private const val CHECK_TICKS = 10L
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
        levelOf(server, binding.dimension)?.let { unindex(it, binding.target, pokemon.uuid) }
        WorkEngine.get()?.onUnbound(pokemon.uuid)
        if (reason != null) {
            val message = Component.translatable(LOST, pokemon.getDisplayName(), Component.translatable("$REASON.$reason"))
            Feedback.chat(binding.binder, server, message)
        }
        return binding
    }

    /** CD-LSH-26: binding data never travels to a new owner. */
    fun onGained(server: MinecraftServer, playerId: UUID, pokemon: Pokemon) {
        val binding = BindingStore.read(pokemon) ?: return
        if (binding.binder != playerId) clear(server, pokemon, TRADED)
    }

    /**
     * CD-LSH-14: after a change of form, the binding stays while a role of the same handler fits the new types, and takes the best
     * of them. Returns false when the binding is gone. A role that the config or a failed handler turned off keeps its bindings.
     */
    fun recheckRole(server: MinecraftServer, pokemon: Pokemon): Boolean {
        val binding = BindingStore.read(pokemon) ?: return false
        val roles = RoleRegistry.current
        val bound = roles.get(binding.roleId) ?: return true
        val types = pokemon.types.map { it.showdownId }
        if (RoleSelection.isEligible(bound.definition, types)) return true
        val next = RoleSelection.best(roles.forTypes(types).map { it.definition }.filter { it.handler == bound.definition.handler })
        if (next == null) {
            clear(server, pokemon, NO_ROLE)
            return false
        }
        BindingStore.write(pokemon, binding.copy(roleId = next.id))
        val level = levelOf(server, binding.dimension) ?: return true
        val index = level.getData(DynamicsAttachments.LEASH_INDEX)
        index.get(binding.target)?.takeIf { it.pokemonId == pokemon.uuid }?.let { index.put(binding.target, it.copy(roleId = next.id)) }
        level.setData(DynamicsAttachments.LEASH_INDEX, index)
        return true
    }

    /** Removes the index entry of [target] when it belongs to [pokemonId]. */
    fun unindex(level: ServerLevel, target: BlockPos, pokemonId: UUID) {
        val index = level.getExistingDataOrNull(DynamicsAttachments.LEASH_INDEX) ?: return
        if (index.get(target)?.pokemonId != pokemonId) return
        index.remove(target)
        level.setData(DynamicsAttachments.LEASH_INDEX, index)
    }

    fun levelOf(server: MinecraftServer, dimension: ResourceLocation): ServerLevel? =
        server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension))

    /** CD-LSH-08, CD-LSH-24, CD-LSH-27, CD-WMD-07: each entry whose pasture or target is loaded. */
    fun checkIndex(level: ServerLevel) {
        val index = level.getExistingDataOrNull(DynamicsAttachments.LEASH_INDEX) ?: return
        for ((target, ref) in index.all()) check(level, target, ref)
    }

    private fun check(level: ServerLevel, target: BlockPos, ref: LeashIndex.Ref) {
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
            if (BindingStore.read(pokemon)?.target != target) return unindex(level, target, ref.pokemonId)
            if (!recheckRole(level.server, pokemon)) return
        }
        if (!level.isLoaded(target)) return
        val handler = RoleRegistry.current.get(ref.roleId)?.handler ?: return
        if (!guarded(handler, true) { handler.stillValid(level, target) }) lost(level, target, ref, TARGET_GONE)
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
