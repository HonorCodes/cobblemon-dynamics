package codes.honor.cobblemondynamics.client

import codes.honor.cobblemondynamics.engine.WorkerStatus
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import javax.imageio.ImageIO

class StateIconsTest {
    private val resources = File(System.getProperty("cd.projectDir"), "src/main/resources/assets")

    private fun file(texture: ResourceLocation) = File(resources, "${texture.namespace}/${texture.path}")

    /** The pixels that the icon draws, as an alpha mask: true where the pixel is mostly opaque. */
    private fun mask(texture: ResourceLocation): BooleanArray {
        val image = ImageIO.read(file(texture))
        assertEquals(16 to 16, image.width to image.height, "$texture must be 16 × 16")
        return BooleanArray(16 * 16) { i -> (image.getRGB(i % 16, i / 16) ushr 24) > 127 }
    }

    @Test
    fun `CD-UI-07 the eight states show seven icons, and ToBed and RestingInPlace share Tired`() {
        val icons = WorkerStatus.entries.associateWith(StateIcons::iconFor)
        assertEquals(8, icons.size)
        assertEquals(7, icons.values.toSet().size)
        assertEquals(StateIcons.TIRED, icons[WorkerStatus.TO_BED])
        assertEquals(StateIcons.TIRED, icons[WorkerStatus.RESTING_IN_PLACE])
        val others = WorkerStatus.entries - setOf(WorkerStatus.TO_BED, WorkerStatus.RESTING_IN_PLACE)
        assertEquals(others.size, others.map(StateIcons::iconFor).toSet().size, "every other state has its own icon")
        assertEquals(icons.values.toSet(), StateIcons.ALL.toSet())
    }

    @Test
    fun `CD-UI-07 each state uses the icon named after it`() {
        assertEquals("textures/gui/state/idle.png", StateIcons.iconFor(WorkerStatus.IDLE).path)
        assertEquals("textures/gui/state/traveling.png", StateIcons.iconFor(WorkerStatus.TRAVELING).path)
        assertEquals("textures/gui/state/working.png", StateIcons.iconFor(WorkerStatus.WORKING).path)
        assertEquals("textures/gui/state/standby.png", StateIcons.iconFor(WorkerStatus.STANDBY).path)
        assertEquals("textures/gui/state/tired.png", StateIcons.iconFor(WorkerStatus.TO_BED).path)
        assertEquals("textures/gui/state/sleeping.png", StateIcons.iconFor(WorkerStatus.SLEEPING).path)
        assertEquals("textures/gui/state/healing.png", StateIcons.iconFor(WorkerStatus.HEALING).path)
    }

    /** Shape, not only colour, tells the icons apart: each pair of alpha masks differs in at least 16 of the 256 pixels. */
    @Test
    fun `CD-UI-08 the seven icons differ in shape, pixel by pixel in their alpha masks`() {
        val masks = StateIcons.ALL.associateWith { texture ->
            assertTrue(file(texture).isFile, "missing ${file(texture)}")
            mask(texture)
        }
        for ((a, maskA) in masks) {
            assertTrue(maskA.count { it } >= 16, "$a draws almost nothing")
            for ((b, maskB) in masks) {
                if (a == b) continue
                val differing = maskA.indices.count { maskA[it] != maskB[it] }
                assertTrue(differing >= 16, "$a and $b differ in only $differing pixels of their alpha masks")
            }
        }
        assertNotEquals(masks[StateIcons.TIRED]?.toList(), masks[StateIcons.SLEEPING]?.toList())
    }
}
