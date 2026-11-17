package codes.honor.cobblemondynamics.handler

import net.minecraft.world.level.block.entity.BlockEntity
import java.util.WeakHashMap

/**
 * What Working workers give the block entities of other mods, which a mixin of that mod reads in the machine's own code. A grant holds
 * through the tick after the one that made it, and its handler renews it on every tick that its worker Works, so a grant whose release
 * was missed, through a handler error or a server stop, still lapses within 2 ticks.
 */
class WorkGrants<V : Any> {
    private class Grant<V>(val value: V, val until: Long)

    // Keyed by the block entity, which no other world and no replaced block shares. Server thread only: a client never reads it.
    private val grants = WeakHashMap<BlockEntity, Grant<V>>()

    fun grant(blockEntity: BlockEntity, value: V, now: Long) {
        grants[blockEntity] = Grant(value, now + 1)
    }

    fun withdraw(blockEntity: BlockEntity) {
        grants.remove(blockEntity)
    }

    /** Forgets every grant when a server stops, because the integrated server stops and starts again in the same JVM. */
    fun reset() {
        grants.clear()
    }

    /** The value that a worker gives [blockEntity] on the current tick, or null. Always null on a client. */
    fun current(blockEntity: BlockEntity): V? {
        val level = blockEntity.level ?: return null
        if (level.isClientSide) return null
        val grant = grants[blockEntity] ?: return null
        if (level.gameTime <= grant.until) return grant.value
        grants.remove(blockEntity)
        return null
    }
}
