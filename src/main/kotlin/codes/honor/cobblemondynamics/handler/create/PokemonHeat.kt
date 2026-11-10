package codes.honor.cobblemondynamics.handler.create

import com.simibubi.create.content.processing.burner.BlazeBurnerBlock
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity
import java.util.WeakHashMap

/**
 * The heat that Working workers give Blaze Burners, which the burner mixin reads. A grant holds through the tick after the one that
 * made it, and the handler renews it on every tick that its worker Works, so heat whose release was missed, through a handler error
 * or a server stop, still lapses within 2 ticks (CD-BRN-06).
 */
object PokemonHeat {
    private class Grant(val heat: BlazeBurnerBlock.HeatLevel, val until: Long)

    // Keyed by the block entity, which no other world and no replaced burner shares. Server thread only: a client never reads it.
    private val grants = WeakHashMap<BlazeBurnerBlockEntity, Grant>()

    fun grant(burner: BlazeBurnerBlockEntity, heat: BlazeBurnerBlock.HeatLevel, now: Long) {
        grants[burner] = Grant(heat, now + 1)
    }

    fun withdraw(burner: BlazeBurnerBlockEntity) {
        grants.remove(burner)
    }

    /** The heat that a worker gives [burner] on the current tick, or null. Always null on a client. */
    @JvmStatic
    fun levelFor(burner: BlazeBurnerBlockEntity): BlazeBurnerBlock.HeatLevel? {
        val level = burner.level ?: return null
        if (level.isClientSide) return null
        val grant = grants[burner] ?: return null
        if (level.gameTime <= grant.until) return grant.heat
        grants.remove(burner)
        return null
    }
}
