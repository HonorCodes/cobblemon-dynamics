package codes.honor.cobblemondynamics.client

import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.RenderType
import net.minecraft.world.phys.AABB
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import net.neoforged.neoforge.common.NeoForge

/**
 * Outlines the targets of the player's own bound workers while the player holds the Work Leash. The server's overview reply holds
 * only the asking player's bindings, so another player's target is never outlined (CD-UI-09).
 */
object TargetOutlines {
    /** Just outside the block, so the lines are not hidden in its faces. */
    private const val OUTSET = 0.002
    private const val RED = 0.95f
    private const val GREEN = 0.75f
    private const val BLUE = 0.2f

    fun register() {
        NeoForge.EVENT_BUS.addListener<RenderLevelStageEvent> { event ->
            if (event.stage == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) render(event)
        }
    }

    /** The stage's pose stack is fresh at this stage and the view rotation is on the model-view stack, so lines are camera-relative. */
    private fun render(event: RenderLevelStageEvent) {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        val targets = ClientState.leashOverview
        if (targets.isEmpty() || !HeldLeash.holds(player)) return
        val camera = event.camera.position
        val pose = event.poseStack
        val buffers = minecraft.renderBuffers().bufferSource()
        val lines = buffers.getBuffer(RenderType.lines())
        pose.pushPose()
        pose.translate(-camera.x, -camera.y, -camera.z)
        for ((_, target) in targets) LevelRenderer.renderLineBox(pose, lines, AABB(target).inflate(OUTSET), RED, GREEN, BLUE, 1f)
        pose.popPose()
        buffers.endBatch(RenderType.lines())
    }
}
