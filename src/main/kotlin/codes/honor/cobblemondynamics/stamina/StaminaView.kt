package codes.honor.cobblemondynamics.stamina

import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.net.StreamCodecs
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import kotlin.math.floor

/**
 * What clients see of a worker for its head bar: Work Stamina as 0 to 100 and the worker's state. The `stamina_view` entity
 * attachment holds it, and NeoForge syncs it to every client that tracks the entity. It is never saved.
 */
data class StaminaView(val percent: Int, val state: WorkerStatus) {
    companion object {
        /** What a new attachment holds, before the engine sets it. */
        val FULL = StaminaView(100, WorkerStatus.IDLE)

        val STREAM_CODEC: StreamCodec<ByteBuf, StaminaView> = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            StaminaView::percent,
            StreamCodecs.WORKER_STATUS,
            StaminaView::state,
            ::StaminaView,
        )

        fun of(stamina: Stamina, state: WorkerStatus): StaminaView = StaminaView(displayPercent(stamina.percent), state)

        /** Rounded down, so a bar reads 100 only when the Pokémon is full. The tiny bias absorbs one-tick rounding error. */
        fun displayPercent(percent: Double): Int = floor(percent + 1e-6).toInt().coerceIn(0, 100)
    }
}
