package codes.honor.cobblemondynamics.net

import codes.honor.cobblemondynamics.engine.WorkerStatus
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec

/** Stream codecs that more than one payload or synced attachment uses. */
object StreamCodecs {
    /** A worker state as its ordinal. An ordinal that this side does not know reads Idle. */
    val WORKER_STATUS: StreamCodec<ByteBuf, WorkerStatus> =
        ByteBufCodecs.VAR_INT.map({ WorkerStatus.entries.getOrElse(it) { WorkerStatus.IDLE } }, WorkerStatus::ordinal)
}
