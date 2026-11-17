package codes.honor.cobblemondynamics.role

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.mojang.serialization.DynamicOps
import net.neoforged.neoforge.common.conditions.ICondition

/**
 * The `neoforge:conditions` of a role file. NeoForge 21.1.265 applies no conditions to a `SimpleJsonResourceReloadListener`, so the
 * role registry tests them before it parses a file (CD-OPT-02, RES9).
 */
object RoleConditions {
    /** NeoForge's `ConditionalOps.DEFAULT_CONDITIONS_KEY`. */
    const val KEY = "neoforge:conditions"

    private const val MOD_LOADED = "neoforge:mod_loaded"

    /**
     * CD-OPT-02: true when the file has no `neoforge:conditions`, or when they all pass in [ops], which the registry makes with
     * `makeConditionalOps()`. Throws when the conditions do not parse.
     */
    fun pass(json: JsonElement, ops: DynamicOps<JsonElement>): Boolean =
        !(json is JsonObject && json.has(KEY)) || ICondition.conditionsMatched(ops, json)

    /** CD-OPT-04: the mod ids of the `neoforge:mod_loaded` conditions of [json], in their order, for the line of the skipped files. */
    fun mods(json: JsonElement): List<String> {
        val conditions = (json as? JsonObject)?.get(KEY) as? JsonArray ?: return emptyList()
        return conditions.mapNotNull { condition ->
            val entry = condition as? JsonObject ?: return@mapNotNull null
            if ((entry.get("type") as? JsonPrimitive)?.asString != MOD_LOADED) return@mapNotNull null
            (entry.get("modid") as? JsonPrimitive)?.asString
        }
    }
}
