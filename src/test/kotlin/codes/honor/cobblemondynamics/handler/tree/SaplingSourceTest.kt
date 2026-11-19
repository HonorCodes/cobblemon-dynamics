package codes.honor.cobblemondynamics.handler.tree

import codes.honor.cobblemondynamics.OnceLogger
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.JUNGLE_LEAVES
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.JUNGLE_LOG
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.OAK_LEAVES
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.OAK_LOG
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.OAK_SAPLING
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.rl
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** CD-LUM-31, CD-LUM-33: the kind of a replant, over plain ids with fake lookups. */
class SaplingSourceTest {
    private val birch = rl("minecraft:birch_sapling")
    private val jungle = rl("minecraft:jungle_sapling")
    private val cherry = rl("minecraft:cherry_sapling")
    private val oak = rl(OAK_SAPLING)
    private val stick = rl("minecraft:stick")

    /** The items that the fake registry holds, and the ones among them that place a block of `#minecraft:saplings`. */
    private val registered = setOf(birch, jungle, cherry, oak, stick, rl("minecraft:cherry_planks"))
    private val saplings = setOf(birch, jungle, cherry, oak)

    private val lines = ArrayList<String>()
    private val warnings = OnceLogger(lines::add)

    private fun kind(
        drops: List<ResourceLocation> = emptyList(),
        leafIds: List<ResourceLocation> = emptyList(),
        log: ResourceLocation? = rl(OAK_LOG),
        tables: Map<ResourceLocation, ResourceLocation> = emptyMap(),
        survives: (ResourceLocation) -> Boolean = { true },
    ): ResourceLocation? = SaplingSource.kind(
        drops = drops,
        leafIds = leafIds,
        baseLogId = log,
        leafSapling = tables::get,
        byId = { id -> id.takeIf(registered::contains) },
        isSapling = saplings::contains,
        survives = survives,
        warnings = warnings,
    )

    @Test
    fun `CD-LUM-31 drops with a birch sapling give birch, before the leaf table and the name rule`() {
        val drops = listOf(rl(OAK_LOG), stick, birch, oak)
        assertEquals(birch, kind(drops, listOf(rl(JUNGLE_LEAVES)), tables = mapOf(rl(JUNGLE_LEAVES) to jungle)))
    }

    @Test
    fun `CD-LUM-31 drops with none and a leaf table that gives a jungle sapling give jungle`() {
        val leaves = listOf(rl(JUNGLE_LEAVES))
        assertEquals(jungle, kind(listOf(rl(JUNGLE_LOG), stick), leaves, rl(JUNGLE_LOG), mapOf(rl(JUNGLE_LEAVES) to jungle)))
    }

    @Test
    fun `CD-LUM-31 the leaves go in the order of the plan, and a leaf with no sapling gives way to the next`() {
        val azaleaLeaves = rl("minecraft:azalea_leaves")
        val tables = mapOf(azaleaLeaves to birch, rl(JUNGLE_LEAVES) to jungle)
        assertEquals(jungle, kind(leafIds = listOf(rl(OAK_LEAVES), rl(JUNGLE_LEAVES), azaleaLeaves), tables = tables))
    }

    @Test
    fun `CD-LUM-31 no table and the log minecraft cherry_log give minecraft cherry_sapling`() {
        assertEquals(cherry, kind(log = rl("minecraft:cherry_log")))
    }

    @Test
    fun `CD-LUM-31 the name rule tries the base log, then each leaf`() {
        assertEquals(cherry, kind(leafIds = listOf(rl("minecraft:cherry_leaves")), log = rl("ns:unknown_log")))
        assertEquals(oak, kind(leafIds = listOf(rl("minecraft:cherry_leaves")), log = rl(OAK_LOG)))
    }

    @Test
    fun `CD-LUM-31 the name rule maps ns x_log and ns x_leaves to ns x_sapling, and nothing else`() {
        assertEquals(rl("ns:x_sapling"), SaplingSource.named(rl("ns:x_log")))
        assertEquals(rl("ns:x_sapling"), SaplingSource.named(rl("ns:x_leaves")))
        assertNull(SaplingSource.named(rl("ns:x_planks")))
        assertNull(SaplingSource.named(rl("ns:log")))
    }

    @Test
    fun `CD-LUM-31 an item that places no block of the sapling tag never counts`() {
        // The stick of the drops and of the leaf table is no sapling, and cherry_wood is no log or leaf id for the name rule.
        assertNull(kind(listOf(stick), listOf(rl("ns:y_leaves")), rl("ns:x_log"), mapOf(rl("ns:y_leaves") to stick)))
        assertNull(kind(log = rl("minecraft:cherry_wood")))
    }

    @Test
    fun `CD-LUM-31 an id of the name rule that the registry does not hold places no sapling`() {
        assertNull(kind(log = rl("ns:palm_log"), leafIds = listOf(rl("ns:palm_leaves"))))
    }

    @Test
    fun `CD-LUM-31 a sapling that cannot survive in the base cell gives way to the next kind`() {
        assertEquals(jungle, kind(listOf(birch), listOf(rl(JUNGLE_LEAVES)), tables = mapOf(rl(JUNGLE_LEAVES) to jungle)) { it != birch })
    }

    @Test
    fun `CD-LUM-33 a log with no leaf table and no name match gives no kind, and two such fells log one line`() {
        repeat(2) { assertNull(kind(listOf(stick), listOf(rl("ns:palm_leaves")), rl("ns:palm_log"))) }
        assertEquals(1, lines.size, "lines: $lines")
        assertTrue("ns:palm_log" in lines.single(), lines.single())
        assertNull(kind(log = rl("ns:other_log")))
        assertEquals(2, lines.size, "another log id logs its own line")
    }

    @Test
    fun `CD-LUM-33 a sapling that only fails to survive logs no line`() {
        assertNull(kind(listOf(oak)) { false })
        assertEquals(emptyList<String>(), lines)
    }
}
