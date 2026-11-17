package codes.honor.cobblemondynamics.handler.xp

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.AABB
import net.neoforged.bus.api.EventPriority
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent

/**
 * CD-SOU-12: the spots where players died in the last [lifetime] ticks. A death drops the player's XP as orbs where the player stood,
 * and the player respawns elsewhere, so a Soul Collector leaves the orbs near a fresh spot as it leaves the orbs near a live player.
 * The spots live in memory only, at most [max] of them, and an expired spot goes at the next look. It names no event, so the unit tests
 * cover it.
 */
class DeathSpots(private val lifetime: Long = LIFETIME_TICKS, private val max: Int = MAX_SPOTS) {
    /** A player died at ([x], [y], [z]) in the level [level], in the game tick [tick] of that level. */
    data class Spot(val level: ResourceLocation, val x: Double, val y: Double, val z: Double, val tick: Long)

    private val spots = ArrayDeque<Spot>()

    val size: Int get() = spots.size

    /** Keeps [spot]. A full memory lets its oldest spot go. */
    fun record(spot: Spot) {
        expire(spot.tick)
        while (spots.size >= max) spots.removeFirst()
        spots.addLast(spot)
    }

    /** The fresh spots of [level] inside [box] at the tick [now]. */
    fun within(level: ResourceLocation, box: AABB, now: Long): List<Spot> {
        expire(now)
        if (spots.isEmpty()) return emptyList()
        return spots.filter { it.level == level && box.contains(it.x, it.y, it.z) }
    }

    fun clear() = spots.clear()

    /** A spot is fresh for [lifetime] ticks from its own tick. One from a later tick than [now] comes from another world, so it goes. */
    private fun expire(now: Long) {
        spots.removeAll { now - it.tick !in 0 until lifetime }
    }

    companion object {
        /** An XP orb despawns 6,000 ticks after it drops, so a spot outlives the orbs of its death. */
        const val LIFETIME_TICKS = 6_000L

        /** Far more deaths than a server has in 6,000 ticks near its pastures, so the bound only keeps the memory small. */
        const val MAX_SPOTS = 256
    }
}

/** The server's [DeathSpots], from NeoForge's death event of each player (CD-SOU-12). */
object PlayerDeaths {
    val spots = DeathSpots()

    fun register() {
        // The lowest priority, and no canceled event: the player then dies and drops the XP where they stand.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingDeathEvent::class.java) { onDeath(it) }
        NeoForge.EVENT_BUS.addListener<ServerStoppedEvent> { spots.clear() }
    }

    private fun onDeath(event: LivingDeathEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val level = player.serverLevel()
        spots.record(DeathSpots.Spot(level.dimension().location(), player.x, player.y, player.z, level.gameTime))
    }
}
