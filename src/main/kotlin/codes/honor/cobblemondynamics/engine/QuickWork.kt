package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.config.WorkEngineSettings
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.pokemon.PokemonSentEvent
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore
import com.cobblemon.mod.common.battles.BattleRegistry
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.PlayerEvent

/** The Cobblemon and NeoForge events of Quick Work (ADR-0013). Server thread only. */
object QuickWork {
    fun register() {
        // Cobblemon stops the emission at the subscriber that cancels a send-out, and LOWEST runs after the other priorities. A
        // send-out that is cancelled when it reaches this subscriber leaves no aim. One that a later LOWEST subscriber cancels leaves
        // an aim that the next aim drops when it is too old to start an order (CD-QWK-25).
        CobblemonEvents.POKEMON_SENT_PRE.subscribe(Priority.LOWEST) { event ->
            EngineTiming.time { WorkEngine.get()?.keepAim(event.pokemon.uuid, if (event.isCanceled) null else aimOf(event)) }
        }
        CobblemonEvents.POKEMON_SENT_POST.subscribe(Priority.NORMAL) { event ->
            EngineTiming.time { WorkEngine.get()?.onSentOut(event.level, event.pokemon, event.pokemonEntity) }
        }
        // CD-QWK-25: Cobblemon posts no POKEMON_SENT_POST for a send-out that a recall cut short, so the recall drops its aim. The
        // entity tick that can call a recall runs on the client too, which must never touch the server's engine.
        CobblemonEvents.POKEMON_RECALL_POST.subscribe(Priority.NORMAL) { event ->
            WorkEngine.get()?.takeIf { it.server.isSameThread }?.keepAim(event.pokemon.uuid, null)
        }
        NeoForge.EVENT_BUS.addListener<PlayerEvent.PlayerLoggedOutEvent> { event -> WorkEngine.get()?.forgetAims(event.entity.uuid) }
    }

    /**
     * CD-QWK-01, CD-QWK-21, CD-CFG-16: the aim of a send-out. Cobblemon posts the event inside its handler of the R key, before the
     * entity exists, so the block in view now is the block that the player meant. The send-out itself ends 30 ticks later.
     */
    private fun aimOf(event: PokemonSentEvent.Pre): QuickAim? {
        val store = event.pokemon.storeCoordinates.get()?.store as? PlayerPartyStore ?: return null
        val owner = event.level.server.playerList.getPlayer(store.playerUUID)?.takeIf { it.level() === event.level } ?: return null
        val s = WorkEngineSettings.current
        val inBattle = BattleRegistry.getBattleByParticipatingPlayer(owner) != null
        val range = QuickWorkRules.traceRange(s.quickWorkEnabled, inBattle, s.quickWorkTargetRange, s.quickWorkLeashRange) ?: return null
        val hit = aimedBlock(owner, range) ?: return null
        return QuickAim(owner.uuid, event.level.dimension().location(), hit.blockPos.immutable(), event.level.gameTime)
    }

    /** The first block that [player]'s view hits within [range] blocks, from the server's copy of its eye position and rotation. */
    fun aimedBlock(player: ServerPlayer, range: Int): BlockHitResult? {
        val eye = player.eyePosition
        val end = eye.add(player.calculateViewVector(player.xRot, player.yRot).scale(range.toDouble()))
        val hit = player.level().clip(ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
        return hit.takeIf { it.type == HitResult.Type.BLOCK }
    }
}
