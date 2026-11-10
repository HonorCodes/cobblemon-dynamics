package codes.honor.cobblemondynamics.role

import com.google.gson.JsonParser
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RoleCodecTest {
    private val id = ResourceLocation.fromNamespaceAndPath("cobblemon_dynamics", "wheel_driver")

    private fun parse(text: String) = RoleCodec.parse(id, JsonParser.parseString(text))

    private val waterGun = """
        { "schema": 1, "handler": "cobblemon_dynamics:water_wheel", "types": ["water"], "auto_assign": true,
          "priority": 0, "range": 6.0, "params": {},
          "effect": { "loop_ticks": 40, "animations": ["watergun", "spray", "beam", "special"],
                      "actor_particles": ["cobblemon:watergun_actor"],
                      "target_particles": ["cobblemon:watergun_spray", "cobblemon:watergun_target"] } }
    """

    @Test
    fun `CD-FX-03 the Water Gun list keeps its order and gains no cry`() {
        val role = parse(waterGun).getOrThrow()
        assertEquals(listOf("watergun", "spray", "beam", "special"), role.effect.animations)
    }

    /** The approver's play-test ruling (2026-11-10): work is silent, and a model's cry animation plays its cry. */
    @Test
    fun `CD-FX-09 a role file that names cry is skipped with an error that names cry`() {
        for (cry in listOf("cry", "Cry")) {
            val result = parse(waterGun.replace("\"spray\",", "\"$cry\", \"spray\","))
            assertTrue(result.error().isPresent, "a file with $cry parsed")
            assertTrue(RoleCodec.describeError(id, result).contains("cry"))
        }
    }

    @Test
    fun `CD-FX-09 a role file that names a sound is skipped`() {
        val sound = waterGun.replace("\"loop_ticks\": 40,", "\"loop_ticks\": 40, \"sound\": \"cobblemon:move.watergun.actor\",")
        val result = parse(sound)
        assertTrue(result.error().isPresent, "a file with a sound parsed")
        assertTrue(RoleCodec.describeError(id, result).contains("sound"))
    }

    @Test
    fun `CD-CFG-07 a file carries types, flag, priority, range, handler, params and effect`() {
        val role = parse(waterGun).getOrThrow()
        assertEquals(setOf("water"), role.types)
        assertTrue(role.autoAssign)
        assertEquals(0, role.priority)
        assertEquals(6.0, role.range)
        assertEquals(ResourceLocation.parse("cobblemon_dynamics:water_wheel"), role.handler)
        assertEquals(40, role.effect.loopTicks)
    }

    @Test
    fun `CD-FX-01 idle_ticks defaults to 40, and a file can set it`() {
        assertEquals(40, parse(waterGun).getOrThrow().effect.idleTicks)
        val idle = waterGun.replace("\"loop_ticks\": 40,", "\"loop_ticks\": 40, \"idle_ticks\": 10,")
        assertEquals(10, parse(idle).getOrThrow().effect.idleTicks)
    }

    @Test
    fun `CD-FX-01 a negative idle_ticks and a loop_ticks under 1 are errors`() {
        assertTrue(parse(waterGun.replace("\"loop_ticks\": 40,", "\"loop_ticks\": 40, \"idle_ticks\": -1,")).error().isPresent)
        assertTrue(parse(waterGun.replace("\"loop_ticks\": 40,", "\"loop_ticks\": 0,")).error().isPresent)
    }

    @Test
    fun `CD-CFG-09 a malformed file is an error that names the file`() {
        val result = parse("""{ "schema": 1, "types": "water" }""")
        assertTrue(result.error().isPresent)
        assertTrue(RoleCodec.describeError(id, result).contains("cobblemon_dynamics:wheel_driver"))
    }

    @Test
    fun `types are lower-cased`() {
        assertEquals(setOf("water"), parse(waterGun.replace("[\"water\"]", "[\"Water\"]")).getOrThrow().types)
    }
}
