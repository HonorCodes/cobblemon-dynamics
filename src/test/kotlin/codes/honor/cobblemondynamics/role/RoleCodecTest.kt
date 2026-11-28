package codes.honor.cobblemondynamics.role

import com.google.gson.JsonParser
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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
    fun `CD-QWK-36 CD-CFG-07 CD-FRZ-02 the Quick Work flag is absent by default, and a file can set it to true or false`() {
        assertNull(parse(waterGun).getOrThrow().quickWork)
        val optedIn = waterGun.replace("\"auto_assign\": true,", "\"auto_assign\": false, \"quick_work\": true,")
        val role = parse(optedIn).getOrThrow()
        assertEquals(true, role.quickWork)
        assertFalse(role.autoAssign)
        val optedOut = parse(waterGun.replace("\"auto_assign\": true,", "\"auto_assign\": true, \"quick_work\": false,")).getOrThrow()
        assertEquals(false, optedOut.quickWork)
        assertTrue(optedOut.autoAssign)
        assertTrue(parse(waterGun.replace("\"auto_assign\": true,", "\"auto_assign\": true, \"quick_work\": \"yes\",")).error().isPresent)
    }

    @Test
    fun `types are lower-cased`() {
        assertEquals(setOf("water"), parse(waterGun.replace("[\"water\"]", "[\"Water\"]")).getOrThrow().types)
    }

    /** The Water Gun file with [keys] after its `auto_assign` key, which they may replace, and [types] in place of its types. */
    private fun withKeys(keys: String, types: String = "[\"water\"]") =
        waterGun.replace("[\"water\"]", types).replace("\"auto_assign\": true,", keys)

    @Test
    fun `CD-PCK-02 type_match is any by default, all needs every type, and another value or no types is an error`() {
        assertFalse(parse(waterGun).getOrThrow().allTypes, "a file with no type_match")
        assertFalse(parse(withKeys("\"auto_assign\": true, \"type_match\": \"any\",")).getOrThrow().allTypes)
        val all = parse(withKeys("\"auto_assign\": true, \"type_match\": \"all\",", "[\"bug\", \"flying\"]")).getOrThrow()
        assertTrue(all.allTypes)
        assertEquals(setOf("bug", "flying"), all.types)
        val bad = parse(withKeys("\"auto_assign\": true, \"type_match\": \"both\","))
        assertTrue(bad.error().isPresent, "type_match both parsed")
        assertTrue(RoleCodec.describeError(id, bad).contains("type_match"))
        assertTrue(parse(withKeys("\"auto_assign\": true, \"type_match\": \"all\",", "[]")).error().isPresent, "all with no types")
    }

    @Test
    fun `CD-PCK-04 leash is true by default, a file can set it to false, and a role that nothing can give is an error`() {
        assertTrue(parse(waterGun).getOrThrow().leash, "a file with no leash key")
        val autoOnly = parse(withKeys("\"auto_assign\": true, \"leash\": false,")).getOrThrow()
        assertFalse(autoOnly.leash)
        assertTrue(autoOnly.autoAssign)
        val nothing = parse(withKeys("\"auto_assign\": false, \"leash\": false,"))
        assertTrue(nothing.error().isPresent, "a role with no auto-assign flag and no leash parsed")
        assertFalse(parse(withKeys("\"auto_assign\": false, \"leash\": false, \"quick_work\": true,")).getOrThrow().leash)
        assertTrue(parse(withKeys("\"auto_assign\": true, \"leash\": \"no\",")).error().isPresent)
    }

    @Test
    fun `CD-PCK-02 type_match reads in any case`() {
        assertTrue(parse(withKeys("\"auto_assign\": true, \"type_match\": \"ALL\",", "[\"bug\", \"flying\"]")).getOrThrow().allTypes)
        assertFalse(parse(withKeys("\"auto_assign\": true, \"type_match\": \"Any\",")).getOrThrow().allTypes)
    }

    @Test
    fun `CD-PRG-05 CD-PRG-08 mastery_moves rare_multiplier and rare_labels parse with clean defaults`() {
        val plain = parse(waterGun).getOrThrow()
        assertEquals(emptySet<String>(), plain.masteryMoves)
        assertEquals(2.0, plain.rareMultiplier, 1e-9)
        assertEquals(setOf("legendary", "mythical"), plain.rareLabels)

        val custom = parse(
            withKeys(
                "\"auto_assign\": true, \"mastery_moves\": [\"Rain Dance\", \"cobblemon:aqua_ring\"], " +
                    "\"rare_multiplier\": 2.5, \"rare_labels\": [\"Legendary\"],",
            ),
        ).getOrThrow()
        assertEquals(setOf("raindance", "aquaring"), custom.masteryMoves)
        assertEquals(2.5, custom.rareMultiplier, 1e-9)
        assertEquals(setOf("legendary"), custom.rareLabels)
    }
}
