package codes.honor.cobblemondynamics.opactest

import com.mojang.authlib.GameProfileRepository
import com.mojang.authlib.yggdrasil.ProfileNotFoundException
import net.minecraft.server.MinecraftServer
import net.minecraft.server.Services
import net.minecraft.server.players.GameProfileCache
import net.neoforged.bus.api.EventPriority
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent

/**
 * Gives the GameTest server the profile cache that a dedicated server has. The vanilla GameTest server has services with no cache, and
 * Open Parties and Claims reads the cache when it loads its claims in its `ServerAboutToStartEvent` listener
 * (`ServerPlayerClaimInfoManager.onAdd`), so the server crashes before any test runs. This listener runs first, at the highest priority.
 */
object TestProfileCache {
    /** Finds no profile, so a lookup by name never goes online. */
    private val NO_PROFILES = GameProfileRepository { names, callback ->
        names.forEach { callback.onProfileLookupFailed(it, ProfileNotFoundException("the GameTest server finds no profiles")) }
    }

    fun register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ServerAboutToStartEvent::class.java) { install(it.server) }
    }

    /** `MinecraftServer.services` is final and `Services` is a record, so this replaces the whole field with a copy that has a cache. */
    private fun install(server: MinecraftServer) {
        if (server.profileCache != null) return
        val cache = GameProfileCache(NO_PROFILES, server.serverDirectory.resolve("usercache.json").toFile())
        // As the MinecraftServer constructor does for a cache in its services. The server clears the executor when it stops.
        cache.setExecutor(server)
        val field = MinecraftServer::class.java.getDeclaredField("services").apply { isAccessible = true }
        val services = field.get(server) as Services
        field.set(server, Services(services.sessionService(), services.servicesKeySet(), services.profileRepository(), cache))
    }
}
