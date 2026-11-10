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

    fun pastureOf(entity: PokemonEntity): BlockPos? = entity.tethering?.pasturePos

    /** The pasture whose panel [player] has open. Cobblemon drops the link in another dimension or beyond 10 blocks. */
    fun linkedPasture(player: ServerPlayer): BlockPos? = PastureLinkManager.getLinkByPlayer(player)?.pos

    fun maxWanderDistance(): Int = Cobblemon.config.pastureMaxWanderDistance
}
