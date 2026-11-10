package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.net.PanelViewers
import codes.honor.cobblemondynamics.pasture.WorkModeService
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.level.ChunkEvent
import net.neoforged.neoforge.event.server.ServerStartingEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.LevelTickEvent

/** The game events that drive the work engine. */
object EngineEvents {
    fun register() {
        NeoForge.EVENT_BUS.addListener<ServerStartingEvent> { WorkEngine.start(it.server) }
        // Cobblemon saves its PC and party stores on ServerStoppedEvent, so Work Stamina is flushed before it.
        NeoForge.EVENT_BUS.addListener<ServerStoppingEvent> { WorkEngine.get()?.flushAll() }
        NeoForge.EVENT_BUS.addListener<ServerStoppedEvent> {
            WorkEngine.stop()
            WorkModeService.reset()
            PanelViewers.reset()
        }
        NeoForge.EVENT_BUS.addListener<PlayerEvent.PlayerLoggedOutEvent> { event ->
            (event.entity as? ServerPlayer)?.let(PanelViewers::forget)
        }
        NeoForge.EVENT_BUS.addListener<LevelTickEvent.Post> { event ->
            val level = event.level as? ServerLevel ?: return@addListener
            WorkEngine.get()?.tickLevel(level)
        }
        NeoForge.EVENT_BUS.addListener<ChunkEvent.Unload> { event ->
            val level = event.level as? ServerLevel ?: return@addListener
            onChunkUnload(level, event.chunk.pos)
        }
    }

    /** What `ChunkEvent.Unload` runs. GameTests call it to unload a chunk that stays loaded. */
    fun onChunkUnload(level: ServerLevel, chunkPos: ChunkPos) {
        WorkEngine.get()?.onChunkUnload(level, chunkPos)
    }
}
