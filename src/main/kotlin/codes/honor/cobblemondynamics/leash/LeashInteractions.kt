package codes.honor.cobblemondynamics.leash

import codes.honor.cobblemondynamics.Feedback
import codes.honor.cobblemondynamics.claim.Claim
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.claim.LeashIndex
import codes.honor.cobblemondynamics.claim.TargetKey
import codes.honor.cobblemondynamics.compat.TeamAllies
import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.engine.WorkArea
import codes.honor.cobblemondynamics.engine.WorkEngine
import codes.honor.cobblemondynamics.engine.guarded
import codes.honor.cobblemondynamics.handler.BindingShape
import codes.honor.cobblemondynamics.pasture.PastureAdapter
import codes.honor.cobblemondynamics.registry.DynamicsAttachments
import codes.honor.cobblemondynamics.registry.DynamicsItems
import codes.honor.cobblemondynamics.role.ActiveRole
import codes.honor.cobblemondynamics.role.RoleDefinition
import codes.honor.cobblemondynamics.role.RoleRegistry
import codes.honor.cobblemondynamics.role.RoleSelection
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
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
    private const val PERMISSION_OPERATOR = 2

    fun register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, PlayerInteractEvent.EntityInteract::class.java) { onEntityInteract(it) }
    }

    /** For Create's hand crank mixin, which lets the leash's own click reach a crank. */
    @JvmStatic
    fun isWorkLeash(stack: ItemStack): Boolean = stack.`is`(DynamicsItems.WORK_LEASH.get())

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
            if (!handleServerEntityClick(player, stack, target)) return
        } else if (target.ownerUUID != player.uuid && !target.hasData(DynamicsAttachments.STAMINA_VIEW)) {
            return
        }
        event.setCanceled(true)
        event.cancellationResult = InteractionResult.SUCCESS
    }

    private fun handleServerEntityClick(player: ServerPlayer, stack: ItemStack, target: PokemonEntity): Boolean {
        val tetheredCtx = PastureAdapter.tetheredContextOf(target)
        if (tetheredCtx != null) {
            val be = tetheredCtx.pasture
            val tethered = tetheredCtx.tethered
            val pokemon = tethered.pokemon ?: target.pokemon
            val actorOwns = tethered.ownerId == player.uuid || pokemon.getOwnerUUID() == player.uuid
            val mayControl = PastureAdapter.mayControl(player, be)
            if (player.isShiftKeyDown) {
                if (!BindRules.mayClear(tetheredToPasture = true, actorOwnsPokemon = actorOwns, mayControlPasture = mayControl)) {
                    return actorOwns
                }
                clearPokemon(player, pokemon)
                return true
            }
            if (!BindRules.maySelect(tetheredToPasture = true, mayControlPasture = mayControl)) return actorOwns
            if (!PastureAdapter.isWorkMode(be)) {
                Feedback.actionBar(player, Component.translatable(BindRules.NOT_WORK_MODE))
                return true
            }
            stack.set(LeashComponents.SELECTION.get(), LeashSelection(pokemon.uuid, pokemon.getDisplayName().string))
            stack.remove(LeashComponents.PENDING_SOURCE.get())
            Feedback.actionBar(player, Component.translatable(SELECTED, pokemon.getDisplayName()))
            return true
        }
        if (target.pokemon.getOwnerUUID() != player.uuid) return false
        if (player.isShiftKeyDown) {
            clearPokemon(player, ownedPokemon(player, target.pokemon.uuid) ?: target.pokemon)
        } else {
            Feedback.actionBar(player, Component.translatable(BindRules.NOT_WORK_MODE))
        }
        return true
    }

    private fun clearPokemon(player: ServerPlayer, pokemon: Pokemon) {
        if (LeashResets.clear(player.server, pokemon, reason = null) != null) {
            Feedback.actionBar(player, Component.translatable(CLEARED, pokemon.getDisplayName()))
        }
    }

    /**
     * A block click with a selection, from `WorkLeashItem.onItemUseFirst` on the server. A click that another mod cancelled
     * never gets here, because NeoForge returns from `useItemOn` before `onItemUseFirst` (CD-SEC-11).
     */
    fun onBlockClick(player: ServerPlayer, stack: ItemStack, clicked: BlockPos, face: Direction) {
        val level = player.serverLevel()
        val now = level.gameTime
        val dimension = level.dimension().location()
        val selection = stack.get(LeashComponents.SELECTION.get())
        val resolved = selection?.let { resolveSelected(player, level, it.pokemonId) }
        val pokemon = resolved?.pokemon
        val pasture = resolved?.pasture
        val area = pasture?.let { WorkArea.of(it, WorkEngineSettings.current, PastureAdapter.maxWanderDistance()) }
        val live = livePending(stack, dimension, now)
        val check = live?.let { BindRules.checkPending(it.pos, area) { pos -> level.isLoaded(pos) } }
        if (check == BindRules.PendingCheck.OUTSIDE_AREA || check == BindRules.PendingCheck.ABSENT) {
            stack.remove(LeashComponents.PENDING_SOURCE.get())
        }
        if (check == BindRules.PendingCheck.OUTSIDE_AREA) {
            refuse(player, stack, BindRules.Outcome.Refuse(BindRules.OUTSIDE_AREA), null)
            return
        }
        val pending = live.takeUnless { check == BindRules.PendingCheck.ABSENT }
        val roles = if (check == BindRules.PendingCheck.NO_AREA) emptyList() else fittingRoles(level, pokemon, clicked, pending)
        val types = pokemon?.types?.map { it.showdownId }.orEmpty()
        val worksAlone = roles.isEmpty() && pending == null && pokemon != null && check != BindRules.PendingCheck.NO_AREA &&
            RoleRegistry.current.forTypes(types).any { !it.definition.leash && it.definition.autoAssign && matches(level, it, clicked) }
        val best = RoleSelection.best(roles.map { it.definition }, types, RoleRegistry::sameGroup)
            ?.let { def -> roles.first { it.definition == def } }
        val canonical = best?.let { role -> guarded(role.handler, clicked) { role.handler.canonical(level, clicked) } } ?: clicked
        val source = pending?.pos ?: canonical
        val index = level.getExistingDataOrNull(DynamicsAttachments.LEASH_INDEX)
        val otherOf = { role: RoleDefinition ->
            index?.exclusiveAt(source, RoleRegistry.laneOf(role.id), RoleRegistry::sharesTargets, RoleRegistry::laneOf)
                ?.let { otherBound(level, it, selection?.pokemonId) }
        }
        val other = best?.let { otherOf(it.definition) }
        val ends = if (pending == null) listOf(canonical) else listOf(pending.pos, canonical)
        val places = if (index != null && best?.handler?.sharesTargets == true) {
            ends.map { BindRules.Place(it, index.placesAt(it, selection?.pokemonId, RoleRegistry::sharesTargets)) }
        } else {
            emptyList()
        }
        val input = BindRules.Input(
            selection = selection,
            pokemonOwnedByPlayer = resolved?.ownedByPlayer == true,
            pastureInWorkMode = pasture != null,
            fittingRoles = roles.map { it.definition },
            canonical = canonical,
            insideWorkArea = area?.contains(source) == true,
            boundToOtherOf = { role -> (if (role == best?.definition) other else otherOf(role))?.seenBy(player) },
            pending = pending,
            sameAsPending = pending?.pos == canonical,
            destinationInReach = pending == null ||
                pasture == null ||
                best == null ||
                guarded(best.handler, false) { best.handler.destinationInReach(best.definition, pasture, canonical) },
            noSpotRefusal = best?.handler?.noSpotRefusal?.takeIf {
                pending == null &&
                    pasture != null &&
                    pokemon != null &&
                    WorkEngine.get()?.hasSpot(level, pasture, pokemon.uuid, best, canonical) == false
            },
            types = types,
            places = places,
            maxWorkers = WorkEngineSettings.current.maxWorkersPerContainer,
            targetRefusal = best?.takeIf { pending == null }?.let { role ->
                guarded<String?>(role.handler, null) { role.handler.bindRefusal(level, canonical, role.definition) }
            },
            worksAlone = worksAlone,
            authorizedOnPasture = resolved?.authorizedOnPasture == true,
        )
        val outcome = BindRules.evaluate(
            input,
            shapeOf = { def -> RoleRegistry.current.get(def.id)?.handler?.bindingShape ?: BindingShape.SINGLE },
            sharesOf = { def -> RoleRegistry.sharesTargets(def.id) },
            sameGroup = RoleRegistry::sameGroup,
        )
        when (outcome) {
            is BindRules.Outcome.Refuse -> refuse(player, stack, outcome, other?.pokemon, pokemon)

            is BindRules.Outcome.AwaitDestination -> {
                val name = pokemon?.getDisplayName() ?: return
                stack.set(LeashComponents.PENDING_SOURCE.get(), PendingSource(dimension, canonical, face, now))
                Feedback.actionBar(player, Component.translatable(SELECT_DESTINATION, name))
            }

            is BindRules.Outcome.Bind -> {
                if (pokemon == null || pasture == null || resolved == null) return
                val binding = if (pending == null) {
                    Binding(outcome.role.id, dimension, pasture, canonical, face, player.uuid)
                } else {
                    Binding(outcome.role.id, dimension, pasture, pending.pos, pending.face, player.uuid, canonical, face)
                }
                bind(player, level, pokemon, binding, resolved.refOwner)
                stack.remove(LeashComponents.SELECTION.get())
                stack.remove(LeashComponents.PENDING_SOURCE.get())
            }
        }
    }

    private class SelectedTarget(
        val pokemon: Pokemon?,
        val pasture: BlockPos?,
        val refOwner: UUID,
        val ownedByPlayer: Boolean,
        val authorizedOnPasture: Boolean,
    )

    private fun resolveSelected(player: ServerPlayer, level: ServerLevel, pokemonId: UUID): SelectedTarget {
        val tetheredCtx = PastureAdapter.workModeTethered(level, pokemonId)
        if (tetheredCtx != null) {
            val mayControl = PastureAdapter.mayControl(player, tetheredCtx.pasture)
            val pokemon = tetheredCtx.tethered.pokemon?.takeIf { mayControl }
            val pasture = tetheredCtx.pasture.blockPos.takeIf { pokemon != null }
            val owned = tetheredCtx.tethered.ownerId == player.uuid || pokemon?.getOwnerUUID() == player.uuid
            return SelectedTarget(
                pokemon = pokemon,
                pasture = pasture,
                refOwner = tetheredCtx.tethered.pcId,
                ownedByPlayer = owned,
                authorizedOnPasture = pokemon != null,
            )
        }
        val own = ownedPokemon(player, pokemonId)
        return SelectedTarget(
            pokemon = own,
            pasture = null,
            refOwner = Cobblemon.storage.getPC(player).uuid,
            ownedByPlayer = own != null,
            authorizedOnPasture = own != null,
        )
    }

    /**
     * The pending source of [stack], if it counts for a click in [dimension] at [now]. A source that expired, or that a click in another
     * level left, is removed from the stack, and the click counts as a first click.
     */
    private fun livePending(stack: ItemStack, dimension: ResourceLocation, now: Long): PendingSource? {
        val pending = stack.get(LeashComponents.PENDING_SOURCE.get()) ?: return null
        if (pending.liveIn(dimension, now, WorkEngineSettings.current.pendingSelectionTicks)) return pending
        stack.remove(LeashComponents.PENDING_SOURCE.get())
        return null
    }

    /**
     * The roles that [pokemon]'s types make it eligible for and that a leash may bind (CD-PCK-04), whose handler matches the clicked block.
     * With a pending source, only source-and-destination roles whose handler still matches the source, in its own level, and the
     * clicked destination.
     */
    private fun fittingRoles(level: ServerLevel, pokemon: Pokemon?, clicked: BlockPos, pending: PendingSource?): List<ActiveRole> {
        pokemon ?: return emptyList()
        val types = pokemon.types.map { it.showdownId }
        return RoleRegistry.current.forLeash(types).filter { role ->
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

    /** A Pokémon bound to a target, and the store id that holds it. */
    private class OtherBound(val pokemon: Pokemon, val owner: UUID) {
        fun seenBy(player: ServerPlayer): BindRules.Bound {
            val owned = owner == player.uuid || pokemon.getOwnerUUID() == player.uuid || Cobblemon.storage.getPC(player).uuid == owner
            return BindRules.Bound(pokemon.getDisplayName().string, pokemon.species.name, ownedByPlayer = owned)
        }
    }

    /** The Pokémon of the index entry [ref], when it is not [selected]. An entry whose Pokémon is gone is LeashResets' to clear. */
    private fun otherBound(level: ServerLevel, ref: LeashIndex.Ref, selected: UUID?): OtherBound? {
        if (ref.pokemonId == selected) return null
        val access = level.registryAccess()
        val pokemon = Cobblemon.storage.getPC(ref.owner, access)[ref.pokemonId]
            ?: Cobblemon.storage.getParty(ref.owner, access)[ref.pokemonId]
            ?: return null
        return OtherBound(pokemon, ref.owner)
    }

    /** CD-LSH-05: the refusal names a Pokémon of the player by its display name, and another player's Pokémon by its species only. */
    private fun refuse(
        player: ServerPlayer,
        stack: ItemStack,
        refusal: BindRules.Outcome.Refuse,
        other: Pokemon?,
        selected: Pokemon? = null,
    ) {
        // CD-LUM-20: the selected Pokémon is the player's own, so the refusal names it by its display name.
        if (refusal.namesWorker) {
            Feedback.actionBar(player, Component.translatable(refusal.messageKey, selected?.getDisplayName() ?: ""))
            return
        }
        val message = when (refusal.messageKey) {
            BindRules.ALREADY_ASSIGNED -> Component.translatable(refusal.messageKey, other?.getDisplayName() ?: refusal.arg)
            BindRules.ALREADY_ASSIGNED_OTHER -> Component.translatable(refusal.messageKey, other?.species?.translatedName ?: refusal.arg)
            BindRules.FULL, BindRules.FULL_ONE -> Component.translatable(refusal.messageKey, containerName(player, refusal.at), refusal.arg)
            else -> Component.translatable(refusal.messageKey)
        }
        // A selection that the server no longer finds as the player's, for example after a trade, is stale.
        if (refusal.messageKey == BindRules.NO_SELECTION) stack.remove(LeashComponents.SELECTION.get())
        Feedback.actionBar(player, message)
    }

    /** CD-LSH-29: the full container of a refusal by its block's name. */
    private fun containerName(player: ServerPlayer, pos: BlockPos?): Any =
        pos?.let { player.serverLevel().getBlockState(it).block.name } ?: ""

    /** CD-LSH-11, CD-TEAM-05: the new binding replaces the old one, and [refOwner] stores the Pokémon's PC store id. */
    private fun bind(player: ServerPlayer, level: ServerLevel, pokemon: Pokemon, binding: Binding, refOwner: UUID) {
        val old = BindingStore.read(pokemon)
        if (old != null) LeashResets.levelOf(player.server, old.dimension)?.let { LeashResets.unindex(it, old.target, pokemon.uuid) }
        BindingStore.write(pokemon, binding)
        val index = level.getData(DynamicsAttachments.LEASH_INDEX)
        index.put(binding.target, LeashIndex.Ref(pokemon.uuid, refOwner, binding.pasture, binding.roleId, binding.destination))
        level.setData(DynamicsAttachments.LEASH_INDEX, index)
        WorkEngine.get()?.onBound(level, pokemon.uuid, binding)
        val target = level.getBlockState(binding.target).block.name
        Feedback.actionBar(player, Component.translatable(BOUND, pokemon.getDisplayName(), target))
    }

    /**
     * A sneak-click with the Work Leash on a target that a worker drives reverses it, for that worker's binder, the placer of its
     * pasture or an operator (CD-WHL-05), and the handler's message for the flip goes to that player (CD-FAN-16). Returns false, with
     * no direction changed, for any other sneak-click, which then goes on to the normal leash rules (CD-SEC-10).
     */
    fun onSneakBlockClick(player: ServerPlayer, clicked: BlockPos): Boolean {
        val level = player.serverLevel()
        val engine = WorkEngine.get() ?: return false
        val dimension = level.dimension().location()
        for (role in RoleRegistry.current.active) {
            if (!matches(level, role, clicked)) continue
            val canonical = guarded(role.handler, clicked) { role.handler.canonical(level, clicked) }
            val key = TargetKey.block(dimension, canonical, role.handler.claimLane)
            val claim = engine.claims.holderOf(key)?.takeIf { it.kind == ClaimKind.WORK } ?: continue
            val handler = engine.roleOf(claim.worker)?.handler ?: return false
            if (!mayFlip(player, level, engine, claim)) return false
            val flipped = guarded(handler, false) { handler.flip(level, canonical) }
            if (flipped) guarded(handler, null) { handler.flipMessage(level, canonical) }?.let { Feedback.actionBar(player, it) }
            return flipped
        }
        return false
    }

    /** A Quick Work claim names no pasture, so no binder and no placer stand behind it, and only an operator flips (CD-SEC-10). */
    private fun mayFlip(player: ServerPlayer, level: ServerLevel, engine: WorkEngine, claim: Claim): Boolean {
        val isOperator = player.hasPermissions(PERMISSION_OPERATOR)
        val pasture = claim.pasture ?: return isOperator
        val binder = engine.pokemonOf(claim.worker)?.let(BindingStore::read)?.binder
        val be = PastureAdapter.pastureAt(level, BlockPos.of(pasture.pos)) ?: return isOperator
        return FlipRules.mayFlip(
            actor = player.uuid,
            isOperator = isOperator,
            binder = binder,
            placer = PastureAdapter.placer(be),
            locked = PastureAdapter.isLocked(be),
            tetheredOwners = PastureAdapter.contributorIds(be),
        ) { a, b -> TeamAllies.areTeammates(player.server, a, b) }
    }

    /** The player's own Pokémon [pokemonId], from the PC, where pastured Pokémon live, or the party. */
    private fun ownedPokemon(player: ServerPlayer, pokemonId: UUID): Pokemon? =
        Cobblemon.storage.getPC(player)[pokemonId] ?: Cobblemon.storage.getParty(player)[pokemonId]
}
