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

    /** The frame sits this far behind the fill, as vanilla puts a name tag's background 0.01 behind its text. */
    private const val BEHIND = 0.01f

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
        val bar = HeadBarRange.BAR
        // The frame first: a buffer source draws each render type's quads when the next type starts, so the fill comes later.
        texturedQuad(buffers.getBuffer(RenderType.text(StaminaBar.HEAD_BAR)), matrix, bar, depth = BEHIND)
        StaminaBar.fill(bar, view.percent)?.let { fill ->
            val crop = fill.width.toFloat() / StaminaBar.inner(bar).width
            val consumer = buffers.getBuffer(RenderType.text(StaminaBar.HEAD_FILL))
            texturedQuad(consumer, matrix, fill, maxU = crop, color = StaminaBar.color(view.percent))
        }
        texturedQuad(buffers.getBuffer(RenderType.text(StateIcons.iconFor(view.state))), matrix, HeadBarRange.ICON)
        pose.popPose()
    }

    /**
     * A quad of [rect] with the texture's columns from 0 to [maxU], tinted with [color] and [depth] behind the name-tag plane.
     * The corners follow vanilla's glyph quads, which face the camera after the name-tag transform.
     */
    private fun texturedQuad(consumer: VertexConsumer, matrix: Matrix4f, rect: Rect, maxU: Float = 1f, color: Int = -1, depth: Float = 0f) {
        val light = LightTexture.FULL_BRIGHT
        val left = rect.x.toFloat()
        val right = rect.right.toFloat()
        val top = rect.y.toFloat()
        val bottom = rect.bottom.toFloat()
        consumer.addVertex(matrix, left, top, depth).setColor(color).setUv(0f, 0f).setLight(light)
        consumer.addVertex(matrix, left, bottom, depth).setColor(color).setUv(0f, 1f).setLight(light)
        consumer.addVertex(matrix, right, bottom, depth).setColor(color).setUv(maxU, 1f).setLight(light)
        consumer.addVertex(matrix, right, top, depth).setColor(color).setUv(maxU, 0f).setLight(light)
    }
}
