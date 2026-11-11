package codes.honor.cobblemondynamics.pasture

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
data class TetheredPokemon(val pokemonId: UUID, val ownerId: UUID, val pokemon: Pokemon?, val entity: PokemonEntity?)

/** The one package that reads Cobblemon pasture internals. */
object PastureAdapter {
    /** The pasture block entity at [pos], which is the bottom half. Never loads a chunk. */
    fun pastureAt(level: ServerLevel, pos: BlockPos): PokemonPastureBlockEntity? =
        if (level.isLoaded(pos)) level.getBlockEntity(pos) as? PokemonPastureBlockEntity else null

    fun isWorkMode(be: PokemonPastureBlockEntity): Boolean = be.getExistingDataOrNull(DynamicsAttachments.WORK_MODE)?.enabled ?: false

    fun placer(be: PokemonPastureBlockEntity): UUID? = be.ownerId

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
            TetheredPokemon(tethering.pokemonId, tethering.playerId, tethering.getPokemon(), entity)
        }
    }

    /** The Pokémon of the pasture list, in Cobblemon's order. */
    fun tetheredIds(be: PokemonPastureBlockEntity): List<UUID> = be.tetheredPokemon.map { it.pokemonId }

    /** The tethering of [pokemonId] in [be], with no entity lookup. Its [TetheredPokemon.pokemon] is null once the PC no longer holds it. */
    fun tetheredPokemon(be: PokemonPastureBlockEntity, pokemonId: UUID): TetheredPokemon? = be.tetheredPokemon
        .firstOrNull { it.pokemonId == pokemonId }
        ?.let { TetheredPokemon(it.pokemonId, it.playerId, it.getPokemon(), null) }

    /** The entity of [pokemonId] that is tethered to the pasture at [pos], as [tethered] finds it, or null. */
    fun tetheredEntity(level: ServerLevel, pos: BlockPos, pokemonId: UUID): PokemonEntity? {
        val be = pastureAt(level, pos) ?: return null
        return tethered(be).firstOrNull { it.pokemonId == pokemonId }?.entity
    }

    fun pastureOf(entity: PokemonEntity): BlockPos? = entity.tethering?.pasturePos

    /** True while [pokemon] is tethered to some pasture. */
    fun isTethered(pokemon: Pokemon): Boolean = pokemon.tetheringId != null

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
}
