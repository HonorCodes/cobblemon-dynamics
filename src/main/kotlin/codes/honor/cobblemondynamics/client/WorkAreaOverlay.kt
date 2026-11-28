package codes.honor.cobblemondynamics.client

import codes.honor.cobblemondynamics.net.AreaBoundsPayload
import codes.honor.cobblemondynamics.net.AreaWatchPayload
import com.cobblemon.mod.common.block.PastureBlock
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.RenderType
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.world.phys.AABB
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.level.LevelEvent

class WorkAreaOverlayState {
    private val watched = LinkedHashSet<BlockPos>()
    private val boxes = LinkedHashMap<BlockPos, AABB>()

    fun toggle(pos: BlockPos): Boolean {
        val key = pos.immutable()
        if (watched.remove(key)) {
            boxes.remove(key)
            return false
        }
        watched.add(key)
        return true
    }

    fun isWatching(pos: BlockPos): Boolean = pos in watched

    fun watchedPositions(): Set<BlockPos> = watched.toSet()

    fun boxAt(pos: BlockPos): AABB? = boxes[pos]

    fun activeBoxes(): List<AABB> = boxes.values.toList()

    fun onBounds(payload: AreaBoundsPayload) {
        val key = payload.pos.immutable()
        if (!payload.active || key !in watched) {
            watched.remove(key)
            boxes.remove(key)
            return
        }
        boxes[key] = payload.box()
    }

    fun pruneMissingPastures(isChunkLoaded: (BlockPos) -> Boolean, isPastureBlock: (BlockPos) -> Boolean): List<BlockPos> {
        if (watched.isEmpty() && boxes.isEmpty()) return emptyList()
        val removed = ArrayList<BlockPos>()
        val candidates = LinkedHashSet<BlockPos>(watched.size + boxes.size)
        candidates.addAll(watched)
        candidates.addAll(boxes.keys)
        for (pos in candidates) {
            if (isChunkLoaded(pos) && !isPastureBlock(pos)) {
                watched.remove(pos)
                boxes.remove(pos)
                removed += pos
            }
        }
        return removed
    }

    fun clear() {
        watched.clear()
        boxes.clear()
    }
}

object WorkAreaOverlay {
    private const val OUTSET = 0.004
    private const val RED = 0.25f
    private const val GREEN = 0.85f
    private const val BLUE = 0.95f
    private const val ALPHA = 0.9f

    val state = WorkAreaOverlayState()
    private var lastLevel: ClientLevel? = null

    fun register() {
        NeoForge.EVENT_BUS.addListener<RenderLevelStageEvent> { event ->
            if (event.stage == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) render(event)
        }
        NeoForge.EVENT_BUS.addListener<LevelEvent.Unload> { event ->
            if (event.level.isClientSide) reset()
        }
    }

    fun toggle(pos: BlockPos): Boolean = state.toggle(pos)

    fun isWatching(pos: BlockPos): Boolean = state.isWatching(pos)

    fun onBounds(payload: AreaBoundsPayload) {
        state.onBounds(payload)
    }

    fun reset() {
        lastLevel = null
        state.clear()
    }

    private fun render(event: RenderLevelStageEvent) {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level
        if (level == null) {
            reset()
            return
        }
        if (lastLevel != null && lastLevel !== level) {
            state.clear()
        }
        lastLevel = level
        val pruned = state.pruneMissingPastures(
            isChunkLoaded = { pos ->
                level.hasChunk(SectionPos.blockToSectionCoord(pos.x), SectionPos.blockToSectionCoord(pos.z))
            },
            isPastureBlock = { pos -> level.getBlockState(pos).block is PastureBlock },
        )
        for (pos in pruned) {
            ClientNet.send(AreaWatchPayload(pos, false))
        }
        val active = state.activeBoxes()
        if (active.isEmpty()) return
        val camera = event.camera.position
        val pose = event.poseStack
        val buffers = minecraft.renderBuffers().bufferSource()
        val lines = buffers.getBuffer(RenderType.lines())
        pose.pushPose()
        pose.translate(-camera.x, -camera.y, -camera.z)
        for (box in active) {
            LevelRenderer.renderLineBox(pose, lines, box.inflate(OUTSET), RED, GREEN, BLUE, ALPHA)
        }
        pose.popPose()
        buffers.endBatch(RenderType.lines())
    }
}
