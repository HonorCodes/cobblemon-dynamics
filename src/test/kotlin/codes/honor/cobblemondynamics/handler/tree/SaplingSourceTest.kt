package codes.honor.cobblemondynamics.handler.tree

import codes.honor.cobblemondynamics.OnceLogger
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.JUNGLE_LEAVES
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.OAK_LEAVES
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.OAK_LOG
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.OAK_SAPLING
import codes.honor.cobblemondynamics.handler.tree.TreeGrid.Companion.SPRUCE_SAPLING
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
    private val spruce = rl(SPRUCE_SAPLING)
    private val oak = rl(OAK_SAPLING)
    private val stick = rl("minecraft:stick")
    private val spruceLeaves = rl("minecraft:spruce_leaves")

    /** The items that the fake registry holds, and the ones among them that place a block of `#minecraft:saplings`. */
    private val registered = setOf(birch, jungle, cherry, oak, spruce, stick, rl("minecraft:cherry_planks"))
    private val saplings = setOf(birch, jungle, cherry, oak, spruce)

    private val lines = ArrayList<String>()
    private val warnings = OnceLogger(lines::add)

    private fun kind(
        leafIds: List<ResourceLocation> = emptyList(),
        log: ResourceLocation? = rl(OAK_LOG),
        tables: Map<ResourceLocation, ResourceLocation> = emptyMap(),
        survives: (ResourceLocation) -> Boolean = { true },
    ): ResourceLocation? = SaplingSource.kind(
        leafIds = leafIds,
        baseLogId = log,
        leafSapling = tables::get,
        byId = { id -> id.takeIf(registered::contains) },
        isSapling = saplings::contains,
        survives = survives,
        warnings = warnings,
    )

    @Test
    fun `CD-LUM-31 a leaf table that gives a jungle sapling gives jungle`() {
        assertEquals(jungle, kind(listOf(rl(JUNGLE_LEAVES)), rl("minecraft:jungle_log"), mapOf(rl(JUNGLE_LEAVES) to jungle)))
    }

    /** The review of 1.2.1: a few oak leaves on a spruce, or a sapling in an item frame, must not decide the kind. */
    @Test
    fun `CD-LUM-31 the most common leaf decides the kind, so a stray oak leaf cluster on a spruce gives spruce`() {
        val tables = mapOf(spruceLeaves to spruce, rl(OAK_LEAVES) to oak)
        assertEquals(spruce, kind(listOf(spruceLeaves, rl(OAK_LEAVES)), rl("minecraft:spruce_log"), tables))
    }

    @Test
    fun `CD-LUM-31 the leaves go in the order of the plan, and a leaf with no sapling gives way to the next`() {
        val azaleaLeaves = rl("minecraft:azalea_leaves")
        val tables = mapOf(azaleaLeaves to birch, rl(JUNGLE_LEAVES) to jungle)
        assertEquals(jungle, kind(listOf(rl("ns:odd_leaves"), rl(JUNGLE_LEAVES), azaleaLeaves), tables = tables))
    }

    @Test
    fun `CD-LUM-31 no table and the log minecraft cherry_log give minecraft cherry_sapling`() {
        assertEquals(cherry, kind(log = rl("minecraft:cherry_log")))
    }

    @Test
    fun `CD-LUM-31 the name rule tries each leaf first and the base log last`() {
        assertEquals(cherry, kind(listOf(rl("minecraft:cherry_leaves")), rl(OAK_LOG)))
        assertEquals(oak, kind(listOf(rl("ns:unknown_leaves")), rl(OAK_LOG)))
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
        // The stick of the leaf table is no sapling, and cherry_wood is no log or leaf id for the name rule.
        assertNull(kind(listOf(rl("ns:y_leaves")), rl("ns:x_log"), mapOf(rl("ns:y_leaves") to stick)))
        assertNull(kind(log = rl("minecraft:cherry_wood")))
    }

    @Test
    fun `CD-LUM-31 an id of the name rule that the registry does not hold places no sapling`() {
        assertNull(kind(listOf(rl("ns:palm_leaves")), rl("ns:palm_log")))
    }

    @Test
    fun `CD-LUM-31 a sapling that cannot survive in the base cell gives way to the next kind`() {
        val tables = mapOf(rl("ns:y_leaves") to birch, rl(JUNGLE_LEAVES) to jungle)
        assertEquals(jungle, kind(listOf(rl("ns:y_leaves"), rl(JUNGLE_LEAVES)), tables = tables) { it != birch })
    }

    @Test
    fun `CD-LUM-33 a log with no leaf table and no name match gives no kind, and two such fells log one line`() {
        repeat(2) { assertNull(kind(listOf(rl("ns:palm_leaves")), rl("ns:palm_log"))) }
        assertEquals(1, lines.size, "lines: $lines")
        assertTrue("ns:palm_log" in lines.single(), lines.single())
        assertNull(kind(log = rl("ns:other_log")))
        assertEquals(2, lines.size, "another log id logs its own line")
    }

    @Test
    fun `CD-LUM-33 a sapling that only fails to survive logs no line`() {
        assertNull(kind(listOf(rl(OAK_LEAVES)), tables = mapOf(rl(OAK_LEAVES) to oak)) { false })
        assertEquals(emptyList<String>(), lines)
    }
}
