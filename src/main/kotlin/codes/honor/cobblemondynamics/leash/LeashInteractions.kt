package codes.honor.cobblemondynamics.leash

import codes.honor.cobblemondynamics.Feedback
import codes.honor.cobblemondynamics.claim.LeashIndex
import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.engine.WorkArea
import codes.honor.cobblemondynamics.engine.WorkEngine
import codes.honor.cobblemondynamics.engine.guarded
import codes.honor.cobblemondynamics.handler.BindingShape
import codes.honor.cobblemondynamics.pasture.PastureAdapter
import codes.honor.cobblemondynamics.registry.DynamicsAttachments
import codes.honor.cobblemondynamics.registry.DynamicsItems
import codes.honor.cobblemondynamics.role.ActiveRole
import codes.honor.cobblemondynamics.role.RoleRegistry
import codes.honor.cobblemondynamics.role.RoleSelection
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.EventPriority
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import java.util.UUID

/** The Work Leash's clicks: select a Pokémon, bind it to a block, clear its binding. The server decides from its own state (CD-SEC-08). */
object LeashInteractions {
    private const val SELECTED = "cobblemon_dynamics.leash.selected"
    private const val CLEARED = "cobblemon_dynamics.leash.cleared"
    private const val BOUND = "cobblemon_dynamics.leash.bound"
    private const val SELECT_DESTINATION = "cobblemon_dynamics.leash.select_destination"

    fun register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, PlayerInteractEvent.EntityInteract::class.java) { onEntityInteract(it) }
    }

    /**
     * CD-LSH-02: a click on one's own Pokémon is cancelled on both sides before `Player.interactOn` calls the entity, so Cobblemon's
     * `mobInteract` never opens its wheel or mounts the Pokémon. A click on another player's or a wild Pokémon goes on (CD-SEC-07).
     */
    private fun onEntityInteract(event: PlayerInteractEvent.EntityInteract) {
        val player = event.entity
        val stack = player.mainHandItem
        if (event.hand != InteractionHand.MAIN_HAND || !stack.`is`(DynamicsItems.WORK_LEASH.get())) return
        val target = event.target as? PokemonEntity ?: return
        if (player is ServerPlayer) {
            // The store, not the entity, says who owns the Pokémon. The entity copies its owner only on its next tick.
            if (target.pokemon.getOwnerUUID() != player.uuid) return
            if (player.isShiftKeyDown) clearOwn(player, target.pokemon) else select(player, stack, target)
        } else if (target.ownerUUID != player.uuid) {
            return
        }
        event.setCanceled(true)
        event.cancellationResult = InteractionResult.SUCCESS
    }

    /** CD-LSH-01: only a Pokémon tethered to a pasture in Work Mode can be selected. */
    private fun select(player: ServerPlayer, stack: ItemStack, entity: PokemonEntity) {
        val pokemon = entity.pokemon
        if (PastureAdapter.workModePastureOf(entity) == null) {
            Feedback.actionBar(player, Component.translatable(BindRules.NOT_WORK_MODE))
            return
        }
        stack.set(LeashComponents.SELECTION.get(), LeashSelection(pokemon.uuid, pokemon.getDisplayName().string))
        stack.remove(LeashComponents.PENDING_SOURCE.get())
        Feedback.actionBar(player, Component.translatable(SELECTED, pokemon.getDisplayName()))
    }

    /** CD-LSH-10: the owner's own sneak-click clears the binding, with no chat message. */
    private fun clearOwn(player: ServerPlayer, pokemon: Pokemon) {
        val stored = ownedPokemon(player, pokemon.uuid) ?: pokemon
        if (LeashResets.clear(player.server, stored, reason = null) != null) {
            Feedback.actionBar(player, Component.translatable(CLEARED, stored.getDisplayName()))
        }
    }

    /**
     * A block click with a selection, from `WorkLeashItem.onItemUseFirst` on the server. A click that another mod cancelled
     * never gets here, because NeoForge returns from `useItemOn` before `onItemUseFirst` (CD-SEC-11).
     */
    fun onBlockClick(player: ServerPlayer, stack: ItemStack, clicked: BlockPos, face: Direction) {
        val level = player.serverLevel()
        val now = level.gameTime
        val selection = stack.get(LeashComponents.SELECTION.get())
        val pending = stack.get(LeashComponents.PENDING_SOURCE.get())?.takeUnless {
            it.expired(now, WorkEngineSettings.current.pendingSelectionTicks)
        }
        val pokemon = selection?.let { ownedPokemon(player, it.pokemonId) }
        val pasture = pokemon?.let { PastureAdapter.workModePastureOf(level, it) }
        val roles = fittingRoles(level, pokemon, clicked, pending)
        val best = RoleSelection.best(roles.map { it.definition })?.let { def -> roles.first { it.definition == def } }
        val canonical = best?.let { role -> guarded(role.handler, clicked) { role.handler.canonical(level, clicked) } } ?: clicked
        // The checks of a source-and-destination binding are the source's, and the destination is the clicked block.
        val source = pending?.pos ?: canonical
        val area = pasture?.let { WorkArea.of(it, WorkEngineSettings.current, PastureAdapter.maxWanderDistance()) }
        val other = otherBound(level, source, selection?.pokemonId)
        val input = BindRules.Input(
            selection = selection,
            pokemonOwnedByPlayer = pokemon != null,
            pastureInWorkMode = pasture != null,
            fittingRoles = roles.map { it.definition },
            canonical = canonical,
            insideWorkArea = area?.contains(source) == true,
            boundToOther = other?.getDisplayName()?.string,
            pending = pending,
            sameAsPending = pending?.pos == canonical,
        )
        val outcome = BindRules.evaluate(input) { def -> RoleRegistry.current.get(def.id)?.handler?.bindingShape ?: BindingShape.SINGLE }
        when (outcome) {
            is BindRules.Outcome.Refuse -> refuse(player, stack, outcome, other)

            is BindRules.Outcome.AwaitDestination -> {
                val name = pokemon?.getDisplayName() ?: return
                stack.set(LeashComponents.PENDING_SOURCE.get(), PendingSource(canonical, face, now))
                Feedback.actionBar(player, Component.translatable(SELECT_DESTINATION, name))
            }

            is BindRules.Outcome.Bind -> {
                if (pokemon == null || pasture == null) return
                val dimension = level.dimension().location()
                val binding = if (pending == null) {
                    Binding(outcome.role.id, dimension, pasture, canonical, face, player.uuid)
                } else {
                    Binding(outcome.role.id, dimension, pasture, pending.pos, pending.face, player.uuid, canonical, face)
                }
                bind(player, level, pokemon, binding)
                stack.remove(LeashComponents.SELECTION.get())
                stack.remove(LeashComponents.PENDING_SOURCE.get())
            }
        }
    }

    /**
     * The roles that [pokemon]'s types make it eligible for, whose handler matches the clicked block. With a pending source, only
     * source-and-destination roles that match the source and the clicked destination.
     */
    private fun fittingRoles(level: ServerLevel, pokemon: Pokemon?, clicked: BlockPos, pending: PendingSource?): List<ActiveRole> {
        pokemon ?: return emptyList()
        val types = pokemon.types.map { it.showdownId }
        return RoleRegistry.current.forTypes(types).filter { role ->
            if (pending == null) {
                matches(level, role, clicked)
            } else {
                role.handler.bindingShape == BindingShape.SOURCE_AND_DESTINATION &&
                    matches(level, role, pending.pos) &&
                    matches(level, role, clicked)
            }
        }
    }

    private fun matches(level: ServerLevel, role: ActiveRole, pos: BlockPos): Boolean =
        guarded(role.handler, false) { role.handler.matches(level, pos, level.getBlockState(pos), level.getBlockEntity(pos)) }

    /** The Pokémon bound to [target], when it is not [selected]. An entry whose Pokémon is gone is LeashResets' to clear. */
    private fun otherBound(level: ServerLevel, target: BlockPos, selected: UUID?): Pokemon? {
        val ref = level.getExistingDataOrNull(DynamicsAttachments.LEASH_INDEX)?.get(target) ?: return null
        if (ref.pokemonId == selected) return null
        val access = level.registryAccess()
        return Cobblemon.storage.getPC(ref.owner, access)[ref.pokemonId] ?: Cobblemon.storage.getParty(ref.owner, access)[ref.pokemonId]
    }

    private fun refuse(player: ServerPlayer, stack: ItemStack, refusal: BindRules.Outcome.Refuse, other: Pokemon?) {
        val message = if (refusal.messageKey == BindRules.ALREADY_ASSIGNED) {
            Component.translatable(refusal.messageKey, other?.getDisplayName() ?: refusal.arg)
        } else {
            Component.translatable(refusal.messageKey)
        }
        // A selection that the server no longer finds as the player's, for example after a trade, is stale.
        if (refusal.messageKey == BindRules.NO_SELECTION) stack.remove(LeashComponents.SELECTION.get())
        Feedback.actionBar(player, message)
    }

    /** CD-LSH-11: the new binding replaces the old one, and the old target is no longer reserved. */
    private fun bind(player: ServerPlayer, level: ServerLevel, pokemon: Pokemon, binding: Binding) {
        val old = BindingStore.read(pokemon)
        if (old != null) LeashResets.levelOf(player.server, old.dimension)?.let { LeashResets.unindex(it, old.target, pokemon.uuid) }
        BindingStore.write(pokemon, binding)
        val index = level.getData(DynamicsAttachments.LEASH_INDEX)
        index.put(binding.target, LeashIndex.Ref(pokemon.uuid, player.uuid, binding.pasture, binding.roleId))
        level.setData(DynamicsAttachments.LEASH_INDEX, index)
        WorkEngine.get()?.onBound(level, pokemon.uuid, binding)
        val target = level.getBlockState(binding.target).block.name
        Feedback.actionBar(player, Component.translatable(BOUND, pokemon.getDisplayName(), target))
    }

    /** The player's own Pokémon [pokemonId], from the PC, where pastured Pokémon live, or the party. */
    private fun ownedPokemon(player: ServerPlayer, pokemonId: UUID): Pokemon? =
        Cobblemon.storage.getPC(player)[pokemonId] ?: Cobblemon.storage.getParty(player)[pokemonId]
}
