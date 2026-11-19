package codes.honor.cobblemondynamics.handler.farming

import codes.honor.cobblemondynamics.role.BuiltInRoles
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The pure rules of the Picker (R95): its targets, ripe fruit, the land claim answers and the area of a pick. */
class PickerRulesTest {
    private val target = BlockPos(4, 70, 9)

    @Test
    fun `CD-PCK-05 an apricorn or a block of the tag with an age is a target, and nothing else`() {
        assertTrue(PickerRules.isTarget(apricorn = true, inTag = false, hasAge = true), "an apricorn")
        assertTrue(PickerRules.isTarget(apricorn = true, inTag = true, hasAge = true), "an apricorn in the tag")
        assertTrue(PickerRules.isTarget(apricorn = false, inTag = true, hasAge = true), "a fruit of a pack")
        assertFalse(PickerRules.isTarget(apricorn = false, inTag = false, hasAge = true), "a crop of no tag")
        assertFalse(PickerRules.isTarget(apricorn = false, inTag = true, hasAge = false), "a tagged block with no age")
    }

    @Test
    fun `CD-PCK-05 a fruit is ripe at the largest age only`() {
        assertTrue(PickerRules.isRipe(3, 3), "an apricorn at age 3")
        assertFalse(PickerRules.isRipe(2, 3), "an apricorn at age 2")
        assertFalse(PickerRules.isRipe(0, 3), "a picked apricorn")
        assertFalse(PickerRules.isRipe(null, 3), "no age")
        assertFalse(PickerRules.isRipe(3, null), "no largest age")
    }

    @Test
    fun `CD-PCK-09 a pick needs both land claim answers, and asks the break only after the change`() {
        assertTrue(PickerRules.mayPick({ true }, { true }))
        assertFalse(PickerRules.mayPick({ true }, { false }), "the break refused")
        var asked = false
        val refused = PickerRules.mayPick({ false }) {
            asked = true
            true
        }
        assertFalse(refused, "the change refused")
        assertFalse(asked, "the break was asked after a refused change")
    }

    /** One cell of a fake tree: each flag is one check of CD-PCK-08, and each one that fails skips the cell. */
    private data class Cell(
        override val inArea: Boolean = true,
        override val loaded: Boolean = true,
        override val stalled: Boolean = false,
        override val worked: Boolean = false,
        override val denied: Boolean = false,
        override val ripe: Boolean = true,
        override val laneFree: Boolean = true,
    ) : PickerRules.AreaCell

    /** The offsets that a walk of [n] picks over [cells], and every other offset holds no fruit. */
    private fun walk(n: Int, cells: Map<Vec3i, Cell>): List<Vec3i> {
        val picked = mutableListOf<Vec3i>()
        CropArea.walk(target, n, { cell ->
            cells[Vec3i(cell.x - target.x, cell.y - target.y, cell.z - target.z)]?.takeIf(PickerRules::eligible)
        }, { cell, _ ->
            picked += Vec3i(cell.x - target.x, cell.y - target.y, cell.z - target.z)
            true
        })
        return picked
    }

    /** Ripe apricorns west, north, south and east of the target, and one a block above it, which comes after those four. */
    private fun tree(): Map<Vec3i, Cell> = listOf(Vec3i(-1, 0, 0), Vec3i(0, 0, -1), Vec3i(0, 0, 1), Vec3i(1, 0, 0), Vec3i(0, 1, 0))
        .associateWith { Cell() }

    @Test
    fun `CD-PCK-08 a level 30 Picker also picks the 3 nearest ripe fruits, in the order of CD-CRP-23`() {
        val n = CropArea.count(30, AreaParams.of(BuiltInRoles.parse("picker").params))
        assertEquals(3, n)
        assertEquals(listOf(Vec3i(-1, 0, 0), Vec3i(0, 0, -1), Vec3i(0, 0, 1)), walk(n, tree()))
    }

    @Test
    fun `CD-PCK-08 the picker file gives N of level divided by 10, at most 10`() {
        val params = AreaParams.of(BuiltInRoles.parse("picker").params)
        assertEquals(listOf(0, 0, 1, 5, 10, 10), listOf(1, 9, 10, 50, 100, 120).map { CropArea.count(it, params) })
    }

    @Test
    fun `CD-PCK-08 each skipped cell gives its place to the next cell`() {
        val skips = listOf(
            Cell(inArea = false),
            Cell(loaded = false),
            Cell(stalled = true),
            Cell(worked = true),
            Cell(denied = true),
            Cell(ripe = false),
            Cell(laneFree = false),
        )
        for (skip in skips) {
            val cells = tree() + (Vec3i(-1, 0, 0) to skip)
            assertEquals(listOf(Vec3i(0, 0, -1), Vec3i(0, 0, 1)), walk(2, cells), "$skip")
        }
    }

    @Test
    fun `CD-PCK-08 a level 9 Picker picks its target only`() {
        assertEquals(emptyList<Vec3i>(), walk(CropArea.count(9, AreaParams.of(BuiltInRoles.parse("picker").params)), tree()))
    }

    /** A cell in a chunk that is not loaded reads nothing more, so no block of an unloaded chunk is read (CD-PCK-08). */
    @Test
    fun `CD-PCK-08 the checks stop at the first that fails`() {
        val read = mutableListOf<String>()
        val cell = object : PickerRules.AreaCell {
            override val inArea get() = true.also { read += "area" }
            override val loaded get() = false.also { read += "loaded" }
            override val stalled get() = false.also { read += "stalled" }
            override val worked get() = false.also { read += "worked" }
            override val denied get() = false.also { read += "denied" }
            override val ripe get() = true.also { read += "ripe" }
            override val laneFree get() = true.also { read += "lane" }
        }
        assertFalse(PickerRules.eligible(cell))
        assertEquals(listOf("area", "loaded"), read)
    }
}
