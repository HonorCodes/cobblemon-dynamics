package codes.honor.cobblemondynamics.pasture

import codes.honor.cobblemondynamics.compat.TeamAllies
import codes.honor.cobblemondynamics.registry.DynamicsAttachments
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pasture.PastureLinkManager
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.AABB
import java.util.UUID

/**
 * One tethered Pokémon of a pasture. [entity] is found in the level by `entity.pokemon.uuid`, because `Pokemon.entity` is null
 * for pastured Pokémon (it reads `ActivePokemonState` only), and `Tethering.entityId` goes stale when the entity reloads.
 */
data class TetheredPokemon(
    val pokemonId: UUID,
    val ownerId: UUID,
    val pokemon: Pokemon?,
    val entity: PokemonEntity?,
    val pcId: UUID = ownerId,
)

/** A pasture block entity together with one of its currently tethered Pokémon entries. */
data class TetheredContext(val pasture: PokemonPastureBlockEntity, val tethered: TetheredPokemon)

/** The one package that reads Cobblemon pasture internals. */
object PastureAdapter {
    private const val PERMISSION_OPERATOR = 2

    /** The pasture block entity at [pos], which is the bottom half. Never loads a chunk. */
    fun pastureAt(level: ServerLevel, pos: BlockPos): PokemonPastureBlockEntity? =
        if (level.isLoaded(pos)) level.getBlockEntity(pos) as? PokemonPastureBlockEntity else null

    fun workModeData(be: PokemonPastureBlockEntity): WorkModeData =
        be.getExistingDataOrNull(DynamicsAttachments.WORK_MODE) ?: WorkModeData.OFF

    fun isWorkMode(be: PokemonPastureBlockEntity): Boolean = workModeData(be).enabled

    fun isLocked(be: PokemonPastureBlockEntity): Boolean = workModeData(be).locked

    fun placer(be: PokemonPastureBlockEntity): UUID? = be.ownerId

    /** The player UUIDs with at least one Cobblemon currently tethered in [be] (CD-TEAM-01). */
    fun contributorIds(be: PokemonPastureBlockEntity): Set<UUID> = be.tetheredPokemon.mapTo(HashSet()) { it.playerId }

    fun mayControl(player: ServerPlayer, be: PokemonPastureBlockEntity): Boolean = PastureAccessPolicy.mayControl(
        actor = player.uuid,
        isOperator = player.hasPermissions(PERMISSION_OPERATOR),
        ownerId = placer(be),
        locked = isLocked(be),
        tetheredOwners = contributorIds(be),
    ) { a, b -> TeamAllies.areTeammates(player.server, a, b) }

    fun tethered(be: PokemonPastureBlockEntity): List<TetheredPokemon> {
        val level = be.level as? ServerLevel ?: return emptyList()
        if (be.tetheredPokemon.isEmpty()) return emptyList()
        val entities = level.getEntitiesOfClass(PokemonEntity::class.java, AABB(be.minRoamPos.center, be.maxRoamPos.center).inflate(1.0)) {
            it.tethering?.pasturePos == be.blockPos
        }
        return be.tetheredPokemon.map { tethering ->
            val entity = entities.firstOrNull {
                it.pokemon.uuid == tethering.pokemonId && it.tethering?.tetheringId == tethering.tetheringId
            }
            val refOwner = PastureOwnerRules.refOwner(tethering.pcId, tethering.playerId) ?: tethering.playerId
            TetheredPokemon(tethering.pokemonId, tethering.playerId, resolvePokemon(level, tethering), entity, refOwner)
        }
    }

    /** The Pokémon of the pasture list, in Cobblemon's order. */
    fun tetheredIds(be: PokemonPastureBlockEntity): List<UUID> = be.tetheredPokemon.map { it.pokemonId }

    /** The tethering of [pokemonId] in [be], with no entity lookup. Its [TetheredPokemon.pokemon] is null once the PC no longer holds it. */
    fun tetheredPokemon(be: PokemonPastureBlockEntity, pokemonId: UUID): TetheredPokemon? {
        val level = be.level as? ServerLevel
        return be.tetheredPokemon.firstOrNull { it.pokemonId == pokemonId }?.let { tethering ->
            val refOwner = PastureOwnerRules.refOwner(tethering.pcId, tethering.playerId) ?: tethering.playerId
            TetheredPokemon(tethering.pokemonId, tethering.playerId, resolvePokemon(level, tethering), null, refOwner)
        }
    }

    /** The entity of [pokemonId] that is tethered to the pasture at [pos], as [tethered] finds it, or null. */
    fun tetheredEntity(level: ServerLevel, pos: BlockPos, pokemonId: UUID): PokemonEntity? {
        val be = pastureAt(level, pos) ?: return null
        return tethered(be).firstOrNull { it.pokemonId == pokemonId }?.entity
    }

    fun pastureOf(entity: PokemonEntity): BlockPos? = entity.tethering?.pasturePos

    /** True while [pokemon] is tethered to some pasture. */
    fun isTethered(pokemon: Pokemon): Boolean = pokemon.tetheringId != null

    /** The pasture and tethered record of [entity], whether Work Mode is on or off, or null when [entity] is not tethered. */
    fun tetheredContextOf(entity: PokemonEntity): TetheredContext? {
        val tethering = entity.tethering ?: return null
        val level = entity.level() as? ServerLevel ?: return null
        val be = pastureAt(level, tethering.pasturePos) ?: return null
        val entry = be.tetheredPokemon.firstOrNull {
            it.tetheringId == tethering.tetheringId && it.pokemonId == entity.pokemon.uuid
        } ?: return null
        val refOwner = PastureOwnerRules.refOwner(entry.pcId, entry.playerId) ?: entry.playerId
        val pokemon = resolvePokemon(level, entry) ?: entity.pokemon
        return TetheredContext(be, TetheredPokemon(entry.pokemonId, entry.playerId, pokemon, entity, refOwner))
    }

    /** The Work Mode pasture in [level] that currently holds [pokemonId] tethered, together with its resolved Pokémon, or null. */
    fun workModeTethered(level: ServerLevel, pokemonId: UUID): TetheredContext? {
        val positions = level.getExistingDataOrNull(DynamicsAttachments.WORK_PASTURES)?.all().orEmpty()
        for (raw in positions) {
            val pos = BlockPos.of(raw)
            val be = pastureAt(level, pos) ?: continue
            if (!isWorkMode(be)) continue
            val entry = be.tetheredPokemon.firstOrNull { it.pokemonId == pokemonId } ?: continue
            val refOwner = PastureOwnerRules.refOwner(entry.pcId, entry.playerId) ?: entry.playerId
            return TetheredContext(be, TetheredPokemon(entry.pokemonId, entry.playerId, resolvePokemon(level, entry), null, refOwner))
        }
        return null
    }

    /** The pasture in Work Mode that [entity] is tethered to, or null. */
    fun workModePastureOf(entity: PokemonEntity): BlockPos? {
        val tethering = entity.tethering ?: return null
        val level = entity.level() as? ServerLevel ?: return null
        val be = pastureAt(level, tethering.pasturePos) ?: return null
        return be.blockPos.takeIf { isWorkMode(be) && be.tetheredPokemon.any { it.tetheringId == tethering.tetheringId } }
    }

    /** The pasture in Work Mode in [level] that holds [pokemon]'s current tethering, or null. */
    fun workModePastureOf(level: ServerLevel, pokemon: Pokemon): BlockPos? {
        val tetheringId = pokemon.tetheringId ?: return null
        val positions = level.getExistingDataOrNull(DynamicsAttachments.WORK_PASTURES)?.all().orEmpty()
        return positions.asSequence().map(BlockPos::of).firstOrNull { pos ->
            val be = pastureAt(level, pos)
            be != null && isWorkMode(be) && be.tetheredPokemon.any { it.pokemonId == pokemon.uuid && it.tetheringId == tetheringId }
        }
    }

    /** The pasture whose panel [player] has open. Cobblemon drops the link in another dimension or beyond 10 blocks. */
    fun linkedPasture(player: ServerPlayer): BlockPos? = PastureLinkManager.getLinkByPlayer(player)?.pos

    fun maxWanderDistance(): Int = Cobblemon.config.pastureMaxWanderDistance

    private fun resolvePokemon(level: ServerLevel?, tethering: PokemonPastureBlockEntity.Tethering): Pokemon? {
        tethering.getPokemon()?.let { return it }
        val access = level?.registryAccess() ?: return null
        for (storeId in PastureOwnerRules.storeIds(tethering.pcId, tethering.playerId)) {
            Cobblemon.storage.getPC(storeId, access)[tethering.pokemonId]?.let { return it }
        }
        return null
    }
}
