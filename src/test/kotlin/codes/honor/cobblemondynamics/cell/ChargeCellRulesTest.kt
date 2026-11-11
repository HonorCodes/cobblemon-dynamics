package codes.honor.cobblemondynamics.cell

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChargeCellRulesTest {
    @Test
    fun `CD-GEN-08 a cell takes FE up to 50,000 and refuses the rest`() {
        val store = CellStore()
        assertEquals(50_000, store.capacity)
        assertEquals(30_000, store.receive(30_000, simulate = true))
        assertEquals(0, store.stored, "a simulated insert changed the store")
        assertEquals(30_000, store.receive(30_000, simulate = false))
        assertEquals(20_000, store.receive(30_000, simulate = false))
        assertEquals(50_000, store.stored)
        assertEquals(0, store.receive(1, simulate = false))
        assertEquals(0, store.receive(-5, simulate = false))
        assertEquals(12_345, store.extract(12_345, simulate = false))
        assertEquals(37_655, store.extract(60_000, simulate = true))
        assertEquals(37_655, store.stored)
    }

    @Test
    fun `CD-GEN-08 a load clamps the FE to the capacity`() {
        val store = CellStore()
        store.load(80_000)
        assertEquals(50_000, store.stored)
        store.load(-1)
        assertEquals(0, store.stored)
    }

    @Test
    fun `CD-GEN-10 the comparator reads 0 empty, 1 for any FE, 8 at half and 15 only when full`() {
        val cases = mapOf(0 to 0, 1 to 1, 3_571 to 1, 3_572 to 2, 25_000 to 8, 49_999 to 14, 50_000 to 15)
        for ((fe, signal) in cases) assertEquals(signal, ChargeCellRules.comparatorSignal(fe), "$fe FE")
    }

    @Test
    fun `CD-GEN-10 the comparator signal never falls as the cell fills, and takes every value from 0 to 15`() {
        val signals = (0..ChargeCellRules.CAPACITY).map(ChargeCellRules::comparatorSignal)
        assertTrue(signals.zipWithNext().all { (a, b) -> b == a || b == a + 1 }, "the signal skips or falls")
        assertEquals((0..15).toList(), signals.distinct())
    }

    @Test
    fun `CD-GEN-09 a face that takes 1,000 and two that take any amount share 10,000 FE as 1,000, 4,500 and 4,500`() {
        assertArrayEquals(intArrayOf(1_000, 4_500, 4_500), ChargeCellRules.split(10_000, intArrayOf(1_000, 10_000, 10_000), 0))
    }

    @Test
    fun `CD-GEN-09 FE that does not divide evenly goes one each in turn, starting at the given face`() {
        assertArrayEquals(intArrayOf(5_001, 5_000), ChargeCellRules.split(10_001, intArrayOf(10_001, 10_001), 0))
        assertArrayEquals(intArrayOf(5_000, 5_001), ChargeCellRules.split(10_001, intArrayOf(10_001, 10_001), 1))
        val extras = (0 until 3).map { first -> ChargeCellRules.split(2, intArrayOf(5, 5, 5), first).toList() }
        assertEquals(listOf(listOf(1, 1, 0), listOf(0, 1, 1), listOf(1, 0, 1)), extras)
    }

    @Test
    fun `CD-GEN-09 the split never gives a face more than it takes, and gives out all FE that the faces can take`() {
        assertArrayEquals(intArrayOf(100, 0, 250), ChargeCellRules.split(10_000, intArrayOf(100, 0, 250), 0))
        assertArrayEquals(intArrayOf(0, 0), ChargeCellRules.split(0, intArrayOf(10, 10), 0))
        assertArrayEquals(intArrayOf(), ChargeCellRules.split(500, intArrayOf(), 0))
        for (first in 0 until 6) {
            val limits = intArrayOf(7, 50_000, 3, 0, 12_000, 49_999)
            val given = ChargeCellRules.split(50_000, limits, first)
            assertEquals(50_000, given.sum(), "first $first")
            assertTrue(given.indices.all { given[it] in 0..limits[it] }, "first $first: ${given.toList()}")
            val open = given.indices.filter { given[it] < limits[it] }.map { given[it] }
            assertTrue(open.isEmpty() || open.max() - open.min() <= 1, "unequal shares between open faces: ${given.toList()}")
        }
    }

    @Test
    fun `CD-GEN-09 a cell levels the neighbouring cells that hold less, and gives nothing to a cell that holds as much or more`() {
        assertArrayEquals(intArrayOf(5_000), ChargeCellRules.levelWith(10_000, intArrayOf(0)))
        assertArrayEquals(intArrayOf(3_333, 3_333), ChargeCellRules.levelWith(10_000, intArrayOf(0, 0)))
        assertArrayEquals(intArrayOf(3_466, 3_066), ChargeCellRules.levelWith(10_000, intArrayOf(0, 400)))
        assertArrayEquals(intArrayOf(0, 50), ChargeCellRules.levelWith(100, intArrayOf(200, 0)))
        assertArrayEquals(intArrayOf(0), ChargeCellRules.levelWith(5_000, intArrayOf(5_000)))
        assertArrayEquals(intArrayOf(0), ChargeCellRules.levelWith(5_001, intArrayOf(5_000)))
        assertArrayEquals(intArrayOf(1), ChargeCellRules.levelWith(5_002, intArrayOf(5_000)))
        assertArrayEquals(intArrayOf(25_000), ChargeCellRules.levelWith(50_000, intArrayOf(0)))
        assertArrayEquals(intArrayOf(), ChargeCellRules.levelWith(50_000, intArrayOf()))
    }

    @Test
    fun `CD-GEN-09 after a cell levels its neighbours it holds at least as much as each cell that it gave FE`() {
        val cases = listOf(10_000 to intArrayOf(0, 400), 600 to IntArray(6), 50_000 to intArrayOf(49_000, 1, 25_000, 0, 7, 12))
        for ((mine, theirs) in cases) {
            val given = ChargeCellRules.levelWith(mine, theirs)
            val kept = mine - given.sum()
            for (i in theirs.indices) {
                val what = "$mine over ${theirs.toList()}: kept $kept, gave ${given.toList()}"
                if (given[i] > 0) assertTrue(kept >= theirs[i] + given[i], what)
            }
        }
    }

    /** Cells that level each other by the rules, each tick in a new order, as block entities tick in an order of their own. */
    private fun settle(cells: IntArray, links: List<Pair<Int, Int>>, ticks: Int): List<Int> {
        val moves = ArrayList<Int>()
        repeat(ticks) { tick ->
            var moved = 0
            for (k in cells.indices) {
                val cell = (k + tick) % cells.size
                val neighbours = links.mapNotNull { (a, b) ->
                    when (cell) {
                        a -> b
                        b -> a
                        else -> null
                    }
                }
                val given = ChargeCellRules.levelWith(cells[cell], neighbours.map { cells[it] }.toIntArray())
                for ((i, other) in neighbours.withIndex()) {
                    cells[cell] -= given[i]
                    cells[other] += given[i]
                    moved += given[i]
                }
            }
            moves += moved
        }
        return moves
    }

    @Test
    fun `CD-GEN-09 two cells of 10,000 and 0 FE settle at 5,000 each, then move nothing`() {
        val cells = intArrayOf(10_000, 0)
        val moves = settle(cells, listOf(0 to 1), ticks = 40)
        assertEquals(listOf(5_000, 5_000), cells.toList())
        assertEquals(5_000, moves.first())
        assertTrue(moves.drop(1).all { it == 0 }, "FE still moves between the cells: $moves")
    }

    @Test
    fun `CD-GEN-09 a ring of four cells and a cell with six neighbours keep their FE and stop moving it`() {
        val ring = intArrayOf(50_000, 0, 7, 33_333)
        val ringMoves = settle(ring, listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0), ticks = 200)
        assertEquals(83_340, ring.sum(), "the ring lost or made FE")
        assertTrue(ringMoves.takeLast(100).all { it == 0 }, "FE still moves around the ring: ${ringMoves.takeLast(10)}")
        val star = intArrayOf(600, 0, 0, 0, 0, 0, 0)
        val starMoves = settle(star, (1..6).map { 0 to it }, ticks = 100)
        assertEquals(600, star.sum(), "the star lost or made FE")
        assertTrue(starMoves.takeLast(50).all { it == 0 }, "FE still moves around the star: ${starMoves.takeLast(10)}")
    }

    @Test
    fun `CD-GEN-11 the tooltip writes FE with a comma between thousands`() {
        assertEquals("12,345", ChargeCellRules.formatFe(12_345))
        assertEquals("50,000", ChargeCellRules.formatFe(50_000))
        assertEquals("0", ChargeCellRules.formatFe(0))
    }
}
