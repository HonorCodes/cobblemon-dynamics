package codes.honor.cobblemondynamics.role

import codes.honor.cobblemondynamics.fx.CobblemonJar
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files

/** One of Cobblemon's move timelines, `data/cobblemon/action_effects/moves/<move>.json`, read from its jar. */
class MoveTimeline(val animations: List<String>, val userParticles: List<String>, val targetParticles: List<String>) {
    companion object {
        fun read(move: String): MoveTimeline {
            val file = CobblemonJar.root.resolve("data/cobblemon/action_effects/moves/$move.json")
            val steps = JsonParser.parseString(Files.readString(file)).asJsonObject.getAsJsonArray("timeline").map { it.asJsonObject }
            val animations = steps.first { it.type == "animation" }.getAsJsonArray("animation").map { it.asString }
            // An entity_particles step plays on the user unless its entity condition asks for another entity.
            val (onTarget, onUser) = steps.filter { it.type == "entity_particles" }.partition {
                it.get("entityCondition")?.asString?.contains("is_user == false") == true
            }
            fun effects(steps: List<JsonObject>) = steps.map { it.get("effect").asString }.distinct()
            return MoveTimeline(animations, effects(onUser), effects(onTarget))
        }

        private val JsonObject.type: String? get() = get("type")?.asString
    }
}
