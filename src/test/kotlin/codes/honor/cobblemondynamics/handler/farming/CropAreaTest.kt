package codes.honor.cobblemondynamics.handler.farming

import com.google.gson.JsonParser
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class CropAreaTest {
    private val target = BlockPos(10, 64, -20)

    /** One cell of a fake field: each flag is one check of CD-CRP-24, and each one that fails skips the cell. */
    private data class Cell(
        val inArea: Boolean = true,
        val loaded: Boolean = true,
        val inTag: Boolean = true,
        val never: Boolean = false,
        val upperHalf: Boolean = false,
        val adapterTakes: Boolean = true,
        val claimedInLane: Boolean = false,
        val reservedInLane: Boolean = false,
        val heldInOtherLane: Boolean = false,
        val landClaimAllows: Boolean = true,
    ) {
        fun counts(): Boolean = inArea && loaded && inTag && !never && !upperHalf && adapterTakes && !claimedInLane && !reservedInLane &&
            landClaimAllows
    }

    /** A walk over a fake field of [cells] at the offsets of the target. A cell with no entry holds no crop. */
    private fun walk(n: Int, cells: Map<Vec3i, Cell>, reads: MutableList<BlockPos> = mutableListOf()): List<Vec3i> {
        val worked = mutableListOf<Vec3i>()
        CropArea.walk(target, n, { pos ->
            reads += pos
            cells[pos.subtract(target)]?.takeIf { it.counts() }
        }, { pos, _ ->
            worked += pos.subtract(target)
            true
        })
        return worked
    }

    private fun field(cell: Cell = Cell()): Map<Vec3i, Cell> = CropArea.offsets.filter { it.y == 0 }.associateWith { cell }

    private fun params(json: String): AreaParams = AreaParams.of(JsonParser.parseString(json).asJsonObject)

    @Test
    fun `CD-CRP-23 the area holds 74 cells, nearest first`() {
        val offsets = CropArea.offsets
        assertEquals(74, offsets.size)
        assertEquals(74, offsets.toSet().size)
        assertTrue(offsets.none { it == Vec3i.ZERO }, "the target is listed")
        assertTrue(offsets.all { abs(it.x) <= 2 && abs(it.z) <= 2 && abs(it.y) <= 1 }, "a cell outside the box is listed")
        assertEquals(
            listOf(Vec3i(-1, 0, 0), Vec3i(0, 0, -1), Vec3i(0, 0, 1), Vec3i(1, 0, 0), Vec3i(0, -1, 0), Vec3i(0, 1, 0)),
            offsets.take(6),
        )
        val key = compareBy<Vec3i>({ it.distSqr(Vec3i.ZERO) }, { abs(it.y) }, { it.y }, { it.x }, { it.z })
        assertEquals(offsets.sortedWith(key), offsets)
        assertSame(offsets, CropArea.offsets)
    }

    @Test
    fun `CD-CRP-25 N is one crop for each 10 levels, at most 10`() {
        val defaults = AreaParams.of(JsonParser.parseString("{}").asJsonObject)
        assertEquals(AreaParams(10, 10), defaults)
        val expected = mapOf(1 to 0, 9 to 0, 10 to 1, 59 to 5, 100 to 10, 150 to 10)
        for ((level, n) in expected) assertEquals(n, CropArea.count(level, defaults), "level $level")
        assertEquals(0, CropArea.count(100, params("""{"levels_per_extra_crop": 0}""")))
        assertEquals(0, CropArea.count(100, params("""{"levels_per_extra_crop": -5}""")))
        assertEquals(AreaParams(10, 10), params("""{"levels_per_extra_crop": "ten", "max_extra_crops": [1]}"""))
        assertEquals(3, CropArea.count(100, params("""{"max_extra_crops": 3}""")))
        assertEquals(0, CropArea.count(100, params("""{"max_extra_crops": -1}""")))
        assertEquals(20, CropArea.count(100, params("""{"levels_per_extra_crop": 5, "max_extra_crops": 50}""")))
    }

    @Test
    fun `CD-CUL-11 a level 30 Cultivator grows the 3 nearest growing crops`() {
        val n = CropArea.count(30, AreaParams.DEFAULT)
        assertEquals(3, n)
        // A fully grown crop and a crop of the never tag: the adapter does not take them. An upper half above the target.
        val cells = field() + mapOf(
            Vec3i(-1, 0, 0) to Cell(adapterTakes = false),
            Vec3i(0, 0, -1) to Cell(never = true),
            Vec3i(0, 1, 0) to Cell(upperHalf = true),
        )
        assertEquals(listOf(Vec3i(0, 0, 1), Vec3i(1, 0, 0), Vec3i(-1, 0, -1)), walk(n, cells))
    }

    @Test
    fun `CD-HRV-24 a level 30 Harvester harvests 3 more crops of a ripe field, and a level 9 one none`() {
        assertEquals(
            listOf(Vec3i(-1, 0, 0), Vec3i(0, 0, -1), Vec3i(0, 0, 1)),
            walk(CropArea.count(30, AreaParams.DEFAULT), field()),
        )
        assertEquals(emptyList<Vec3i>(), walk(CropArea.count(9, AreaParams.DEFAULT), field()))
    }

    @Test
    fun `CD-CRP-24 each skipped cell gives its place to the next cell`() {
        val skips = listOf(
            Cell(inArea = false),
            Cell(loaded = false),
            Cell(inTag = false),
            Cell(never = true),
            Cell(upperHalf = true),
            Cell(adapterTakes = false),
            Cell(claimedInLane = true),
            Cell(reservedInLane = true),
            Cell(landClaimAllows = false),
        )
        for (skip in skips) {
            val cells = field() + (Vec3i(0, 0, -1) to skip)
            assertEquals(listOf(Vec3i(-1, 0, 0), Vec3i(0, 0, 1)), walk(2, cells), "$skip")
        }
        val other = field() + (Vec3i(0, 0, -1) to Cell(heldInOtherLane = true))
        assertEquals(listOf(Vec3i(-1, 0, 0), Vec3i(0, 0, -1)), walk(2, other), "a claim of the other lane skips its cell")
    }

    @Test
    fun `CD-CRP-24 a cell that the act refuses or leaves unchanged does not count, and the walk goes on`() {
        val worked = mutableListOf<Vec3i>()
        val count = CropArea.walk(target, 2, { Unit }, { pos, _ ->
            val offset = pos.subtract(target)
            if (offset == Vec3i(0, 0, -1)) return@walk false
            worked += offset
            true
        })
        assertEquals(2, count)
        assertEquals(listOf(Vec3i(-1, 0, 0), Vec3i(0, 0, 1)), worked)
    }

    @Test
    fun `CD-CRP-24 each cell is read just before its act`() {
        // A BlockPos equals the Vec3i of the same cell, but its text differs, so the events hold offsets, not strings.
        val events = mutableListOf<Pair<String, Vec3i>>()
        CropArea.walk(target, 2, { pos ->
            events += "read" to pos.subtract(target)
            Unit
        }, { pos, _ ->
            events += "act" to pos.subtract(target)
            true
        })
        val a = Vec3i(-1, 0, 0)
        val b = Vec3i(0, 0, -1)
        assertEquals(listOf("read" to a, "act" to a, "read" to b, "act" to b), events)
    }

    @Test
    fun `CD-CRP-24 a check or an act that throws skips its cell only, with the block read before the work`() {
        val failed = mutableListOf<Pair<Vec3i, String?>>()
        val worked = mutableListOf<Vec3i>()
        val count = CropArea.walk(target, 2, { pos ->
            if (pos.subtract(target) == Vec3i(-1, 0, 0)) throw IllegalStateException("a crop of another mod")
            "crop at ${pos.subtract(target).toShortString()}"
        }, { pos, _ ->
            if (pos.subtract(target) == Vec3i(0, 0, -1)) throw NoSuchMethodError("a changed method")
            worked += pos.subtract(target)
            true
        }, { pos, found, _ -> failed += pos.subtract(target) to found })
        assertEquals(2, count)
        assertEquals(listOf(Vec3i(0, 0, 1), Vec3i(1, 0, 0)), worked)
        assertEquals(listOf(Vec3i(-1, 0, 0) to null, Vec3i(0, 0, -1) to "crop at 0, 0, -1"), failed)
    }

    @Test
    fun `CD-CRP-24 a use or a harvest changes cells inside the area only`() {
        val crop = BlockPos(1, 64, 1)
        assertEquals(listOf(crop, crop.above()), CropArea.changedCells(crop, null))
        assertEquals(listOf(crop, crop.above(), crop.east()), CropArea.changedCells(crop, crop.east()))
    }

    @Test
    fun `CD-CRP-06 two areas of one lane never work one cell in the same tick`() {
        val keys = TickKeys<String>()
        val server = Any()
        assertTrue(keys.take(server, 10, "harvester 1 2 3"))
        assertTrue(keys.isTaken(server, 10, "harvester 1 2 3"))
        assertFalse(keys.take(server, 10, "harvester 1 2 3"))
        assertTrue(keys.take(server, 10, "cultivator 1 2 3"), "a claim in the other lane does not stop the area")
        assertFalse(keys.isTaken(server, 11, "harvester 1 2 3"))
        assertTrue(keys.take(server, 11, "harvester 1 2 3"), "the next tick starts an empty set")
        assertTrue(keys.take(Any(), 11, "harvester 1 2 3"), "another server starts an empty set")
    }

    @Test
    fun `CD-LUM-29 a block that failed in an area stays out of it until the server stops`() {
        val denied = SessionKeys<String>()
        val server = Any()
        assertFalse(denied.contains(server, "Harvester:mod:crop"))
        assertTrue(denied.add(server, "Harvester:mod:crop"))
        assertFalse(denied.add(server, "Harvester:mod:crop"))
        assertTrue(denied.contains(server, "Harvester:mod:crop"))
        assertFalse(denied.contains(server, "Cultivator:mod:crop"))
        assertFalse(denied.contains(Any(), "Harvester:mod:crop"), "a new server starts with no denied block")
    }

    @Test
    fun `CD-CRP-20 one use at N 10 works at most 11 crops and reads at most 74 cells`() {
        val n = CropArea.count(100, AreaParams.DEFAULT)
        val reads = mutableListOf<BlockPos>()
        val all = CropArea.offsets.associateWith { Cell() }
        assertEquals(10, walk(n, all, reads).size)
        assertEquals(10, reads.size)
        reads.clear()
        // A field with no crop beside the target reads each cell once, and no more.
        assertEquals(0, walk(n, emptyMap(), reads).size)
        assertEquals(74, reads.size)
        assertEquals(74, reads.toSet().size)
        reads.clear()
        assertEquals(0, walk(0, all, reads).size)
        assertEquals(0, reads.size)
    }

    @Test
    fun `CD-CUL-12 a use costs its ticks once when one of its calls grew something`() {
        assertEquals(100, CropArea.cost(anyUseReturnedTrue = true, staminaTicksPerUse = 100))
        assertEquals(0, CropArea.cost(anyUseReturnedTrue = false, staminaTicksPerUse = 100))
        assertEquals(0, CropArea.cost(anyUseReturnedTrue = true, staminaTicksPerUse = -3))
    }
}
