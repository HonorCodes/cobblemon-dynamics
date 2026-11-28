package codes.honor.cobblemondynamics.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.EnumSet

class BrainBridgeOrderTest {
    @Test
    fun `CD-NAV-10 clearResidualTargets erases registered walk and anger memories and stops navigation`() {
        val held = EnumSet.allOf(BrainBridge.ResidualMemory::class.java)
        val order = ArrayList<String>()

        BrainBridge.clearResidualTargets(
            erase = { memory ->
                held.remove(memory)
                order += "erase:${memory.name}"
            },
            stopNavigation = { order += "stopNav" },
        )

        assertTrue(held.isEmpty())
        assertEquals(
            listOf(
                "erase:WALK_TARGET",
                "erase:ATTACK_TARGET",
                "erase:ANGRY_AT",
                "erase:CANT_REACH_WALK_TARGET_SINCE",
                "stopNav",
            ),
            order,
        )
    }
}
