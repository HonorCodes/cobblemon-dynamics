package codes.honor.cobblemondynamics.role

import codes.honor.cobblemondynamics.handler.capability.GeneratorParams
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.ceil

/** The balance rows over the mod's own role files (ADR-0010): each test parses the files and computes the bound that its row states. */
class BalanceFilesTest {
    private val roles: List<RoleDefinition> by lazy {
        BuiltInRoles.dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().sortedBy { it.name }.map(BuiltInRoles::parse)
    }

    private fun withHandler(handler: String): List<RoleDefinition> = roles.filter { it.handler.toString() == handler }

    /** The curve's level is clamped to 1 to 100, so these levels hold every rate that a worker can give. */
    private fun topRate(params: GeneratorParams): Int =
        (1..100).maxOf { level -> maxOf(params.fePerTick(level, emptySet()), params.fePerTick(level, params.rareLabels)) }

    @Test
    fun `CD-BAL-01 the default Generator tops out below 15,000 FE per tick`() {
        val generators = withHandler("cobblemon_dynamics:fe_push")
        assertTrue(generators.isNotEmpty(), "no built-in role file uses the FE handler")
        for (role in generators) {
            val top = topRate(GeneratorParams.of(role.params))
            assertTrue(top < 15_000, "${role.id} allows $top FE per tick per worker")
        }
        assertEquals(10_000, topRate(GeneratorParams.of(BuiltInRoles.parse("generator").params)))
    }

    /** ARCHITECTURE section 6: a worker moves up to ceil(items_per_second_at_level_100 × L ÷ 100) items per second. */
    @Test
    fun `CD-BAL-05 Telekinesis and Magnet move at most 64 items per second if their files exist`() {
        for (name in listOf("telekinesis", "magnet")) {
            val file = File(BuiltInRoles.dir, "$name.json")
            if (!file.exists()) continue
            val atLevel100 = BuiltInRoles.parse(file).params.get(ITEMS_PER_SECOND_AT_LEVEL_100)
            assertNotNull(atLevel100, "$name.json has no $ITEMS_PER_SECOND_AT_LEVEL_100")
            val top = (1..100).maxOf { level -> ceil(atLevel100.asDouble * level / 100).toInt() }
            assertTrue(top <= 64, "$name.json lets one worker move $top items per second")
        }
    }

    private companion object {
        const val ITEMS_PER_SECOND_AT_LEVEL_100 = "items_per_second_at_level_100"
    }
}
