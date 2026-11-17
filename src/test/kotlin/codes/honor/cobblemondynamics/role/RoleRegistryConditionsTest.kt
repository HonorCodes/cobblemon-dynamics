package codes.honor.cobblemondynamics.role

import codes.honor.cobblemondynamics.handler.HandlerRegistry
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RoleRegistryConditionsTest {
    private val handler = ResourceLocation.fromNamespaceAndPath("cobblemon_dynamics_test", "conditions")

    private fun file(conditions: String?): JsonElement {
        val key = if (conditions == null) "" else "\"neoforge:conditions\": $conditions,"
        val body = """"schema": 1, "handler": "$handler", "types": ["fire"], "auto_assign": true, "effect": { "animations": ["ember"] }"""
        return JsonParser.parseString("{ $key $body }")
    }

    private fun condition(modId: String) = """{ "type": "neoforge:mod_loaded", "modid": "$modId" }"""

    private fun modLoaded(vararg modIds: String) = modIds.joinToString(", ", "[", "]") { condition(it) }

    private fun id(path: String) = ResourceLocation.fromNamespaceAndPath("cobblemon_dynamics", path)

    /** NeoForge's `neoforge:mod_loaded` with [present] as the loaded mods: unit tests have no mod list and no condition registry. */
    private fun conditionsWith(present: Set<String>): (JsonElement) -> Boolean = { json -> RoleConditions.mods(json).all { it in present } }

    @Test
    fun `CD-OPT-02 a file whose mod is absent is skipped before parsing and is in neither count`() {
        if (HandlerRegistry.get(handler) == null) HandlerRegistry.register(FakeHandler(handler))
        val absent = id("heater_mekanism")
        val present = id("stoker_test")
        val plain = id("stoker_plain")
        val entries = mapOf(absent to file(modLoaded("mekanism")), present to file(modLoaded("minecraft")), plain to file(null))
        val previous = RoleRegistry.applyForTest(emptyMap())
        try {
            RoleRegistry.load(entries, conditionsWith(setOf("minecraft")))
            assertEquals(setOf(present, plain), RoleRegistry.loadedIds(), "the loaded files")
            assertNotNull(RoleRegistry.current.get(present), "the file whose mod is present did not load")
            assertNotNull(RoleRegistry.current.get(plain), "the file with no conditions did not load")
            assertFalse(absent in RoleRegistry.current.disabled, "the skipped file counts as disabled")
            assertEquals(mapOf(absent to listOf("mekanism")), RoleRegistry.skipped(), "the skipped files and their mods")
        } finally {
            RoleRegistry.applyForTest(previous)
        }
    }

    @Test
    fun `CD-OPT-02 a file with no conditions passes without NeoForge's conditions`() {
        // Plain JSON ops hold no condition context, so the test fails if the check reads them for a file with no conditions.
        assertTrue(RoleConditions.pass(file(null), JsonOps.INSTANCE))
    }

    @Test
    fun `CD-OPT-04 the mods of a file are the mod ids of its mod_loaded conditions`() {
        assertEquals(listOf("mekanism", "mekanismgenerators"), RoleConditions.mods(file(modLoaded("mekanism", "mekanismgenerators"))))
        assertEquals(emptyList<String>(), RoleConditions.mods(file(null)))
        assertEquals(emptyList<String>(), RoleConditions.mods(file("""[{ "type": "neoforge:true" }]""")))
    }
}
