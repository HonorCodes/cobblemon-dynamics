package codes.honor.cobblemondynamics.xp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class XpLevelsTest {
    @Test
    fun `CD-XPC-04 0, 7, 352 and 100,000 XP take a player at level 0 to the levels 0, 1, 16 and 166`() {
        val cases = mapOf(0 to 0, 7 to 1, 352 to 16, 100_000 to 166)
        for ((points, level) in cases) assertEquals(level, XpLevels.levelFor(points), "$points XP")
    }

    @Test
    fun `CD-XPC-04 a level starts at exactly its XP, as vanilla's curve counts it`() {
        assertEquals(0, XpLevels.levelFor(6))
        assertEquals(15, XpLevels.levelFor(351))
        assertEquals(30, XpLevels.levelFor(1_395))
        assertEquals(29, XpLevels.levelFor(1_394))
        assertEquals(listOf(7, 9, 37, 42, 112, 121), listOf(0, 1, 15, 16, 30, 31).map(XpLevels::neededForNext))
    }
}
