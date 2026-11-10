package codes.honor.cobblemondynamics.client

import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.neoforged.neoforge.network.PacketDistributor

/** Sends client payloads. A screen can close after the connection is gone, and then there is no server to tell. */
object ClientNet {
    fun send(payload: CustomPacketPayload) {
        if (Minecraft.getInstance().connection == null) return
        PacketDistributor.sendToServer(payload)
    }
}
