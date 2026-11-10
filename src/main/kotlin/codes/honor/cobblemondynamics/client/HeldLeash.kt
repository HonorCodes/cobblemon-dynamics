package codes.honor.cobblemondynamics.client

import codes.honor.cobblemondynamics.net.LeashOverviewRequestPayload
import codes.honor.cobblemondynamics.registry.DynamicsItems
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.common.NeoForge

/** Whether the player holds the Work Leash in the main hand, which shows the head bars and the outlines (CD-UI-06, CD-UI-09). */
object HeldLeash {
    private val requests = OverviewRequests()

    fun register() {
        NeoForge.EVENT_BUS.addListener<ClientTickEvent.Post> { tick() }
    }

    fun holds(player: Player): Boolean = player.mainHandItem.`is`(DynamicsItems.WORK_LEASH.get())

    private fun tick() {
        val player = Minecraft.getInstance().player
        if (requests.tick(player != null && holds(player))) ClientNet.send(LeashOverviewRequestPayload)
    }
}
