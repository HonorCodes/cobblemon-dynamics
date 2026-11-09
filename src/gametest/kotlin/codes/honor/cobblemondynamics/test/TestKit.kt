package codes.honor.cobblemondynamics.test

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.mojang.authlib.GameProfile
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.AABB
import net.neoforged.neoforge.common.util.FakePlayerFactory
import java.util.UUID

/** Builds GameTest scenes: owners, pastures and Pokémon. */
object TestKit {
    /**
     * A fake owner with a unique profile. Vanilla's mock player joins the player list, and its connection rejects
     * Cobblemon's login payloads, so the tests use NeoForge's FakePlayer, whose connection drops every packet.
     */
    fun owner(helper: GameTestHelper, name: String = "owner"): ServerPlayer {
        val uuid = UUID.randomUUID()
        val player = FakePlayerFactory.get(helper.level, GameProfile(uuid, "${name}_${uuid.toString().take(6)}"))
        val center = helper.absoluteVec(BlockPos(1, 1, 1).bottomCenter)
        player.moveTo(center.x, center.y, center.z)
        return player
    }

    fun placePasture(helper: GameTestHelper, rel: BlockPos, owner: ServerPlayer?): PokemonPastureBlockEntity {
        val level = helper.level
        val pos = helper.absolutePos(rel)
        val state = CobblemonBlocks.PASTURE.defaultBlockState()
        level.setBlock(pos, state, 3)
        CobblemonBlocks.PASTURE.setPlacedBy(level, pos, state, owner, ItemStack.EMPTY)
        return level.getBlockEntity(pos) as? PokemonPastureBlockEntity
            ?: throw GameTestAssertException("no pasture block entity at $pos")
    }

    fun pokemon(spec: String): Pokemon = PokemonProperties.parse(spec).create()

    fun tether(helper: GameTestHelper, pasture: PokemonPastureBlockEntity, owner: ServerPlayer, spec: String): PokemonEntity {
        val pokemon = pokemon(spec)
        Cobblemon.storage.getPC(owner).add(pokemon)
        if (!pasture.tether(owner, pokemon, Direction.SOUTH)) throw GameTestAssertException("tether failed for $spec")
        return pasturedEntity(helper, pasture, pokemon.uuid) ?: throw GameTestAssertException("no entity after tether for $spec")
    }

    /** Pokemon.entity is set only for sent-out Pokémon, so a pastured entity is found in the level by its Pokémon UUID. */
    fun pasturedEntity(helper: GameTestHelper, pasture: PokemonPastureBlockEntity, pokemonId: UUID): PokemonEntity? =
        helper.level.getEntitiesOfClass(PokemonEntity::class.java, AABB(pasture.blockPos).inflate(48.0)) {
            it.pokemon.uuid == pokemonId && it.tethering != null
        }.firstOrNull()

    fun wild(helper: GameTestHelper, rel: BlockPos, spec: String): PokemonEntity {
        val entity = PokemonEntity(helper.level, pokemon(spec))
        val pos = helper.absoluteVec(rel.bottomCenter)
        entity.moveTo(pos.x, pos.y, pos.z)
        helper.level.addFreshEntity(entity)
        return entity
    }
}
