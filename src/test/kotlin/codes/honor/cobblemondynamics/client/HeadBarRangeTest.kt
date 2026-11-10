package codes.honor.cobblemondynamics.client

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HeadBarRangeTest {
    private val viewer = Vec3(0.5, 64.0, 0.5)

    @Test
    fun `CD-UI-06 a worker 16_0 blocks away shows its head bar and one 16_1 blocks away does not`() {
        assertTrue(HeadBarRange.inRange(viewer, viewer.add(16.0, 0.0, 0.0), 16))
        assertFalse(HeadBarRange.inRange(viewer, viewer.add(16.1, 0.0, 0.0), 16))
        assertTrue(HeadBarRange.inRange(viewer, viewer.add(0.0, 0.0, -16.0), 16))
        assertFalse(HeadBarRange.inRange(viewer, viewer.add(0.0, 0.0, -16.1), 16))
    }

    @Test
    fun `the range is a sphere, so height counts too`() {
        assertTrue(HeadBarRange.inRange(viewer, viewer.add(0.0, 16.0, 0.0), 16))
        assertFalse(HeadBarRange.inRange(viewer, viewer.add(0.0, 16.1, 0.0), 16))
        assertTrue(HeadBarRange.inRange(viewer, viewer.add(3.0, 4.0, 0.0), 5))
        assertFalse(HeadBarRange.inRange(viewer, viewer.add(3.0, 4.1, 0.0), 5))
    }

    @Test
    fun `the range follows hud_headBarRange`() {
        assertTrue(HeadBarRange.inRange(viewer, viewer.add(4.0, 0.0, 0.0), 4))
        assertFalse(HeadBarRange.inRange(viewer, viewer.add(4.1, 0.0, 0.0), 4))
    }

    @Test
    fun `the head bar's icon and bar sit side by side, centred above the head`() {
        val icon = HeadBarRange.ICON
        val bar = HeadBarRange.BAR
        assertFalse(icon.overlaps(bar))
        assertEquals(-(bar.right), icon.x, "the pair must be centred")
        assertEquals(icon.y + icon.height / 2, bar.y + bar.height / 2, "the bar must be centred on the icon")
    }
}
