package codes.honor.cobblemondynamics.client

import codes.honor.cobblemondynamics.config.ClientConfig
import codes.honor.cobblemondynamics.registry.DynamicsAttachments
import codes.honor.cobblemondynamics.stamina.StaminaView
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.neoforged.neoforge.client.event.RenderLivingEvent
import net.neoforged.neoforge.common.NeoForge
import org.joml.Matrix4f

/**
 * Draws a Work Stamina bar and a state icon over each worker within `hud.headBarRange` blocks, only while the player holds the
 * Work Leash (CD-UI-06). It reads the `stamina_view` attachment that the server syncs, so it needs no mixin (CD-UI-13).
 */
object HeadBars {
    /** The name-tag scale: one pixel of [HeadBarRange.BAR] is 1/40 of a block. */
    private const val SCALE = 0.025f

    /** Above the head, clear of where a name tag would sit. */
    private const val HEIGHT_ABOVE_HEAD = 0.75

    fun register() {
        NeoForge.EVENT_BUS.addListener(RenderLivingEvent.Post::class.java) { onRender(it) }
    }

    private fun onRender(event: RenderLivingEvent.Post<*, *>) {
        val entity = event.entity as? PokemonEntity ?: return
        val player = Minecraft.getInstance().player ?: return
        if (!HeldLeash.holds(player)) return
        val view = entity.getExistingDataOrNull(DynamicsAttachments.STAMINA_VIEW) ?: return
        if (!HeadBarRange.inRange(player.position(), entity.position(), ClientConfig.headBarRange)) return
        draw(event.poseStack, event.multiBufferSource, entity, view)
    }

    /** As vanilla draws a name tag: turned to the camera, with y pointing down, so the parts read as GUI pixels. */
    private fun draw(pose: PoseStack, buffers: MultiBufferSource, entity: PokemonEntity, view: StaminaView) {
        pose.pushPose()
        pose.translate(0.0, entity.bbHeight + HEIGHT_ABOVE_HEAD, 0.0)
        pose.mulPose(Minecraft.getInstance().entityRenderDispatcher.cameraOrientation())
        pose.scale(SCALE, -SCALE, SCALE)
        val matrix = pose.last().pose()
        val flat = buffers.getBuffer(RenderType.textBackground())
        // The parts do not overlap, so the coplanar quads never fight over depth.
        for ((rect, color) in StaminaBar.parts(HeadBarRange.BAR, view.percent)) quad(flat, matrix, rect, color)
        texturedQuad(buffers.getBuffer(RenderType.text(StateIcons.iconFor(view.state))), matrix, HeadBarRange.ICON)
        pose.popPose()
    }

    /** The corners in the order of vanilla's glyph quads, which face the camera after the name-tag transform. */
    private fun quad(consumer: VertexConsumer, matrix: Matrix4f, rect: Rect, color: Int) {
        val light = LightTexture.FULL_BRIGHT
        consumer.addVertex(matrix, rect.x.toFloat(), rect.y.toFloat(), 0f).setColor(color).setLight(light)
        consumer.addVertex(matrix, rect.x.toFloat(), rect.bottom.toFloat(), 0f).setColor(color).setLight(light)
        consumer.addVertex(matrix, rect.right.toFloat(), rect.bottom.toFloat(), 0f).setColor(color).setLight(light)
        consumer.addVertex(matrix, rect.right.toFloat(), rect.y.toFloat(), 0f).setColor(color).setLight(light)
    }

    private fun texturedQuad(consumer: VertexConsumer, matrix: Matrix4f, rect: Rect) {
        val light = LightTexture.FULL_BRIGHT
        val white = -1
        consumer.addVertex(matrix, rect.x.toFloat(), rect.y.toFloat(), 0f).setColor(white).setUv(0f, 0f).setLight(light)
        consumer.addVertex(matrix, rect.x.toFloat(), rect.bottom.toFloat(), 0f).setColor(white).setUv(0f, 1f).setLight(light)
        consumer.addVertex(matrix, rect.right.toFloat(), rect.bottom.toFloat(), 0f).setColor(white).setUv(1f, 1f).setLight(light)
        consumer.addVertex(matrix, rect.right.toFloat(), rect.y.toFloat(), 0f).setColor(white).setUv(1f, 0f).setLight(light)
    }
}
