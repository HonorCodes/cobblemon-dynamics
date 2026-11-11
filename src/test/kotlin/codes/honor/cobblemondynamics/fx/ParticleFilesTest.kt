package codes.honor.cobblemondynamics.fx

import codes.honor.cobblemondynamics.role.BuiltInRoles
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Every particle that the mod can send, checked against Cobblemon's own particle files in its jar on the test classpath. The client
 * crashes while it ticks a particle whose MoLang calls `math.random()` with no arguments, as `cobblemon:heal_sparkles` does.
 */
class ParticleFilesTest {
    private class ParticleFile(val path: Path, val text: String, val spawns: Set<ResourceLocation>, val sound: Boolean)

    /** Cobblemon's particle files by their `identifier`, the id that its client knows them by. */
    private val files: Map<ResourceLocation, ParticleFile> by lazy {
        val root = CobblemonJar.bedrock.resolve("particles")
        val paths = Files.walk(root).use { all -> all.filter { it.fileName.toString().endsWith(".particle.json") }.toList() }
        paths.associate { path ->
            val text = Files.readString(path)
            val effect = JsonParser.parseString(text).asJsonObject.getAsJsonObject("particle_effect")
            val id = ResourceLocation.parse(effect.getAsJsonObject("description").get("identifier").asString)
            val spawns = HashSet<ResourceLocation>()
            val events = effect.get("events")
            events?.let { collectSpawns(it, spawns) }
            id to ParticleFile(path, text, spawns, events?.toString()?.contains("\"sound_effect\"") == true)
        }
    }

    /** The particles that the events of a particle spawn, such as `ember_fire` from `ember_actor`. */
    private fun collectSpawns(element: JsonElement, into: MutableSet<ResourceLocation>) {
        when {
            element.isJsonObject -> for ((key, value) in element.asJsonObject.entrySet()) {
                val effect = value.takeIf { key == "particle_effect" && it.isJsonObject }?.asJsonObject?.get("effect")
                effect?.asString?.let(ResourceLocation::tryParse)?.let(into::add)
                collectSpawns(value, into)
            }

            element.isJsonArray -> element.asJsonArray.forEach { collectSpawns(it, into) }
        }
    }

    /** The particles of every built-in role file, the heal fallback and the sleep loop. */
    private val sent: Set<ResourceLocation> by lazy {
        val roles = BuiltInRoles.dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().flatMap { file ->
            val effect = BuiltInRoles.parse(file).effect
            effect.actorParticles + effect.targetParticles
        }
        (roles + HealEffect.FALLBACK_PARTICLES + SleepEffect.PARTICLES).toSet()
    }

    @Test
    fun `every particle that the mod sends, and every particle that one spawns, has a file with no argument-less math_random()`() {
        val problems = mutableListOf<String>()
        val seen = HashSet<ResourceLocation>()
        val queue = ArrayDeque(sent)
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (!seen.add(id)) continue
            val file = files[id]
            if (file == null) {
                problems += "$id has no particle file in Cobblemon's jar"
                continue
            }
            if (NO_ARGUMENT_RANDOM.containsMatchIn(file.text)) problems += "$id (${file.path}) calls math.random() with no arguments"
            queue += file.spawns
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    /** A particle event can play a sound, as Cobblemon's `bite_target` does. Work is silent (the approver's play-test ruling, 2026-11-10). */
    @Test
    fun `CD-FX-09 no particle that the mod sends, and no particle that one spawns, plays a sound`() {
        val loud = HashSet<ResourceLocation>()
        val seen = HashSet<ResourceLocation>()
        val queue = ArrayDeque(sent)
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            val file = files[id]
            if (!seen.add(id) || file == null) continue
            if (file.sound) loud += id
            queue += file.spawns
        }
        assertTrue(loud.isEmpty(), "particles with a sound event: $loud")
        assertTrue(files.getValue(ResourceLocation.parse("cobblemon:bite_target")).sound, "the check misses the sound of bite_target")
    }

    @Test
    fun `the check finds the argument-less math_random() call in heal_sparkles`() {
        val sparkles = files.getValue(ResourceLocation.parse("cobblemon:heal_sparkles"))
        assertTrue(NO_ARGUMENT_RANDOM.containsMatchIn(sparkles.text), "no match in ${sparkles.path}")
    }

    @Test
    fun `the check covers every fixed particle id of the fx package`() {
        val sources = File(System.getProperty("cd.projectDir"), "src/main/kotlin/codes/honor/cobblemondynamics/fx")
        val fixed = sources.listFiles { f -> f.extension == "kt" }.orEmpty().flatMapTo(HashSet()) { file ->
            PARSED_ID.findAll(file.readText()).map { ResourceLocation.parse(it.groupValues[1]) }.toList()
        } - HealEffect.FLORAL_HEALING
        assertTrue(fixed.isNotEmpty(), "no ResourceLocation.parse calls in $sources")
        assertTrue(sent.containsAll(fixed), "fixed particle ids that the check does not cover: ${fixed - sent}")
    }

    private companion object {
        /** MoLang is case-insensitive. Cobblemon's `math.random` takes a low and a high bound. */
        val NO_ARGUMENT_RANDOM = Regex("""math\.random\(\s*\)""", RegexOption.IGNORE_CASE)

        val PARSED_ID = Regex("""ResourceLocation\.parse\("([a-z0-9_.-]+:[a-z0-9_/.-]+)"\)""")
    }
}
