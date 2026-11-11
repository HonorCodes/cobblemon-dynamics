package codes.honor.cobblemondynamics.net

import codes.honor.cobblemondynamics.claim.LeashIndex
import codes.honor.cobblemondynamics.engine.WorkEngine
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.pasture.PastureAdapter
import codes.honor.cobblemondynamics.registry.DynamicsAttachments
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import io.netty.buffer.ByteBuf
import net.minecraft.core.BlockPos
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer

/** Client to server: the player took the Work Leash in hand. */
data object LeashOverviewRequestPayload : CustomPacketPayload {
    val TYPE = CustomPacketPayload.Type<LeashOverviewRequestPayload>(id("leash_overview_request"))
    val STREAM_CODEC: StreamCodec<ByteBuf, LeashOverviewRequestPayload> = StreamCodec.unit(this)

    override fun type(): CustomPacketPayload.Type<LeashOverviewRequestPayload> = TYPE
}

/**
 * Server to client: the player's own bound workers in the player's level, as (entity id, target), so the client outlines only its
 * own targets (CD-UI-09). The entity id is -1 when the worker's entity is not loaded.
 */
data class LeashOverviewPayload(val entries: List<Pair<Int, BlockPos>>) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<LeashOverviewPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<LeashOverviewPayload>(id("leash_overview"))

        private val ENTRY: StreamCodec<ByteBuf, Pair<Int, BlockPos>> = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            Pair<Int, BlockPos>::first,
            BlockPos.STREAM_CODEC,
            Pair<Int, BlockPos>::second,
        ) { entityId, target -> entityId to target }

        val STREAM_CODEC: StreamCodec<ByteBuf, LeashOverviewPayload> =
            ENTRY.apply(ByteBufCodecs.list()).map(::LeashOverviewPayload, LeashOverviewPayload::entries)

        /** The reply to [player]'s request. A worker in a pasture with Work Mode off has no engine slot, so its pasture finds it. */
        fun of(player: ServerPlayer): LeashOverviewPayload {
            val level = player.serverLevel()
            val owned = level.getExistingDataOrNull(DynamicsAttachments.LEASH_INDEX)?.allOwnedBy(player.uuid).orEmpty()
            val engine = WorkEngine.get()
            val entries = owned.map { (target, ref) ->
                val entity = engine?.entityOf(ref.pokemonId) ?: pasturedEntity(level, ref)
                (entity?.id ?: -1) to target
            }
            return LeashOverviewPayload(entries)
        }

        private fun pasturedEntity(level: ServerLevel, ref: LeashIndex.Ref): PokemonEntity? {
            val be = PastureAdapter.pastureAt(level, ref.pasture) ?: return null
            return PastureAdapter.tethered(be).firstOrNull { it.pokemonId == ref.pokemonId }?.entity
        }
    }
}
