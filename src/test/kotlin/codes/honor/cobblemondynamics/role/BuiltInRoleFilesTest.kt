package codes.honor.cobblemondynamics.role

import codes.honor.cobblemondynamics.engine.WorkSpots
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class BuiltInRoleFilesTest {
    private data class Row(
        val handler: String,
        val types: Set<String>,
        val autoAssign: Boolean,
        val priority: Int,
        val firstAnimation: String,
    )

    /** The Role table of the spec sheet, by role id. M4 adds the other Phase 1 roles. */
    private val roleTable = mapOf(
        "stoker" to Row("cobblemon_dynamics:furnace", setOf("fire"), true, 0, "flamethrower"),
        "wheel_driver" to Row("cobblemon_dynamics:water_wheel", setOf("water"), true, 0, "watergun"),
        "cranker" to Row("cobblemon_dynamics:hand_crank", setOf("fighting", "normal"), true, 0, "pound"),
        "burner_keeper" to Row("cobblemon_dynamics:burner_heat", setOf("fire"), true, 0, "flamethrower"),
        "superheater" to Row("cobblemon_dynamics:burner_heat", setOf("dragon"), true, 10, "dragonbreath"),
        "generator" to Row("cobblemon_dynamics:fe_push", setOf("electric"), true, 0, "thundershock"),
        "crystal_tender" to Row("cobblemon_dynamics:budding", setOf("rock"), true, 0, "rockthrow"),
        "brew_stoker" to Row("cobblemon_dynamics:brewing_fuel", setOf("fire"), true, 10, "ember"),
        "brewer" to Row("cobblemon_dynamics:brewing_speed", setOf("poison"), true, 0, "poisongas"),
        "pump" to Row("cobblemon_dynamics:fluid_fill", setOf("water"), false, 0, "watergun"),
    )

    /** The `auto_assign` key as the file writes it, so that a missing key fails as well as `true`. */
    private fun autoAssignKey(name: String): JsonElement? {
        val json = JsonParser.parseString(File(BuiltInRoles.dir, "$name.json").readText())
        return json.asJsonObject.get("auto_assign")
    }

    /**
     * Cobblemon's `watergun` timeline emits the spray and the actor burst from the user, aimed at the target, and only the two
     * `target` particles on the target. Target particles play at the work focus entity, so the spray must be an actor particle.
     */
    @Test
    fun `CD-FX-04 the Wheel Driver sprays from the worker and splashes at the wheel`() {
        val effect = BuiltInRoles.parse("wheel_driver").effect
        assertEquals(listOf("cobblemon:watergun_spray", "cobblemon:watergun_actor"), effect.actorParticles.map { it.toString() })
        assertEquals(listOf("cobblemon:watergun_target", "cobblemon:watergun_targetfoam"), effect.targetParticles.map { it.toString() })
        assertEquals(listOf("watergun", "spray", "beam", "special", "cry"), effect.animationsWithCry)
    }

    /** Cobblemon's `pound` timeline emits both of its particles on the target only. */
    @Test
    fun `CD-FX-04 the Cranker's Pound lands on the crank`() {
        val effect = BuiltInRoles.parse("cranker").effect
        assertEquals(emptyList<String>(), effect.actorParticles.map { it.toString() })
        assertEquals(listOf("cobblemon:pound_target", "cobblemon:pound_targetsparks"), effect.targetParticles.map { it.toString() })
        assertEquals(listOf("pound", "punch", "tail", "physical", "cry"), effect.animationsWithCry)
    }

    /** Cobblemon's `flamethrower` timeline sends the stream from the user, and the hit and the burst land on the target. */
    @Test
    fun `CD-FX-04 the Burner Keeper's Flamethrower streams from the worker and bursts on the burner`() {
        val effect = BuiltInRoles.parse("burner_keeper").effect
        assertEquals(listOf("cobblemon:flamethrower_actor"), effect.actorParticles.map { it.toString() })
        assertEquals(
            listOf("cobblemon:flamethrower_target", "cobblemon:flamethrower_targetburst"),
            effect.targetParticles.map { it.toString() },
        )
        assertEquals(listOf("flamethrower", "fire", "spray", "special", "cry"), effect.animationsWithCry)
    }

    /** Cobblemon's `dragonbreath` timeline sends the breath from the user, and only the hit lands on the target. */
    @Test
    fun `CD-FX-04 the Superheater's Dragon Breath streams from the worker and hits the burner`() {
        val effect = BuiltInRoles.parse("superheater").effect
        assertEquals(listOf("cobblemon:dragonbreath_actor"), effect.actorParticles.map { it.toString() })
        assertEquals(listOf("cobblemon:dragonbreath_target"), effect.targetParticles.map { it.toString() })
        assertEquals(listOf("dragonbreath", "spray", "special", "cry"), effect.animationsWithCry)
    }

    /**
     * Cobblemon's `thundershock` timeline emits its sparks, its bolts and the shock from the user, the shock aimed at the target, and
     * three hits on the target only.
     */
    @Test
    fun `CD-FX-04 the Generator's Thunder Shock sparks from the worker and strikes the target`() {
        val effect = BuiltInRoles.parse("generator").effect
        assertEquals(
            listOf("cobblemon:thundershock_actorsparks", "cobblemon:thundershock_actorbolts", "cobblemon:thundershock_actor"),
            effect.actorParticles.map { it.toString() },
        )
        assertEquals(
            listOf("cobblemon:thundershock_target", "cobblemon:thundershock_targetbolts", "cobblemon:thundershock_targetimpact"),
            effect.targetParticles.map { it.toString() },
        )
        assertEquals(listOf("thundershock", "electric", "special", "cry"), effect.animationsWithCry)
    }

    /**
     * Cobblemon's `rockthrow` timeline emits one particle, from the user, aimed at the target. Its hit and its stars are events of
     * that thrown rock, so the client plays them where the rock lands, and the role file names no target particle.
     */
    @Test
    fun `CD-FX-04 the Crystal Tender's Rock Throw is thrown from the worker`() {
        val effect = BuiltInRoles.parse("crystal_tender").effect
        assertEquals(listOf("cobblemon:rockthrow_actor"), effect.actorParticles.map { it.toString() })
        assertEquals(emptyList<String>(), effect.targetParticles.map { it.toString() })
        assertEquals(listOf("rockthrow", "rock", "physical", "cry"), effect.animationsWithCry)
    }

    /** Cobblemon's `ember` timeline sends the ember from the user, aimed at the target, and only the hit lands on the target. */
    @Test
    fun `CD-FX-04 the Brew Stoker's Ember flies from the worker and burns at the stand`() {
        val effect = BuiltInRoles.parse("brew_stoker").effect
        assertEquals(listOf("cobblemon:ember_actor"), effect.actorParticles.map { it.toString() })
        assertEquals(listOf("cobblemon:ember_target"), effect.targetParticles.map { it.toString() })
        assertEquals(listOf("ember", "fire", "spray", "special", "cry"), effect.animationsWithCry)
    }

    /** Cobblemon's `poisongas` timeline sends the gas from the user, aimed at the target, and only the cloud forms on the target. */
    @Test
    fun `CD-FX-04 the Brewer's Poison Gas drifts from the worker and clouds the stand`() {
        val effect = BuiltInRoles.parse("brewer").effect
        assertEquals(listOf("cobblemon:poisongas_actor"), effect.actorParticles.map { it.toString() })
        assertEquals(listOf("cobblemon:poisongas_target"), effect.targetParticles.map { it.toString() })
        assertEquals(listOf("poisongas", "spray", "special", "cry"), effect.animationsWithCry)
    }

    /** The Pump casts the Wheel Driver's Water Gun: the spray and the actor burst from the worker, the splash on the target. */
    @Test
    fun `CD-FX-04 the Pump's Water Gun sprays from the worker and splashes at the target`() {
        val effect = BuiltInRoles.parse("pump").effect
        assertEquals(listOf("cobblemon:watergun_spray", "cobblemon:watergun_actor"), effect.actorParticles.map { it.toString() })
        assertEquals(listOf("cobblemon:watergun_target", "cobblemon:watergun_targetfoam"), effect.targetParticles.map { it.toString() })
        assertEquals(listOf("watergun", "spray", "beam", "special", "cry"), effect.animationsWithCry)
    }

    @Test
    fun `CD-PMP-02 the Pump role file sets auto_assign to false`() {
        val key = autoAssignKey("pump")
        assertTrue(key != null && key.isJsonPrimitive && key.asJsonPrimitive.isBoolean && !key.asBoolean, "pump.json has auto_assign $key")
        assertFalse(BuiltInRoles.parse("pump").autoAssign)
    }

    /** The Cranker is the only role whose move needs contact. */
    @Test
    fun `CD-ASG-10 every built-in ranged role works from 6 blocks`() {
        val files = BuiltInRoles.dir.listFiles { f -> f.name.endsWith(".json") && f.name != "cranker.json" }.orEmpty()
        for (file in files) assertEquals(6.0, BuiltInRoles.parse(file).range, file.name)
    }

    @Test
    fun `CD-CRK-01 Machop and Rattata may crank, and Pikachu may not`() {
        val cranker = BuiltInRoles.parse("cranker")
        assertTrue(RoleSelection.isEligible(cranker, listOf("fighting")), "Machop")
        assertTrue(RoleSelection.isEligible(cranker, listOf("normal")), "Rattata")
        assertFalse(RoleSelection.isEligible(cranker, listOf("electric")), "Pikachu")
    }

    @Test
    fun `CD-ASG-10 the default role files give the Stoker 6 blocks and the Cranker 2`() {
        val center = Vec3(0.5, 1.5, 0.5)
        val stoker = BuiltInRoles.parse("stoker").range
        assertTrue(WorkSpots.inRange(center.add(6.0, 0.0, 0.0), center, stoker))
        assertFalse(WorkSpots.inRange(center.add(6.1, 0.0, 0.0), center, stoker))
        val cranker = BuiltInRoles.parse("cranker").range
        assertTrue(WorkSpots.inRange(center.add(0.0, 0.0, 2.0), center, cranker))
        assertFalse(WorkSpots.inRange(center.add(0.0, 0.0, 2.1), center, cranker))
    }

    @Test
    fun `CD-FX-02 every built-in role file matches the Role table`() {
        val files = BuiltInRoles.dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().sortedBy { it.name }
        val actual = files.associate { f ->
            val role = BuiltInRoles.parse(f)
            val row = Row(role.handler.toString(), role.types, role.autoAssign, role.priority, role.effect.animations.first())
            f.nameWithoutExtension to row
        }
        assertEquals(roleTable, actual)
    }
}
