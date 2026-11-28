package codes.honor.cobblemondynamics.fx

import codes.honor.cobblemondynamics.OnceLogger
import codes.honor.cobblemondynamics.role.EffectDefinition
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.resources.ResourceLocation
import net.neoforged.fml.ModList
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** A move's animation names and Snowstorm particle ids parsed from Cobblemon's action effect data (CD-PRG-06). */
data class MasteryTimeline(
    val animations: List<String>,
    val actorParticles: List<ResourceLocation>,
    val targetParticles: List<ResourceLocation>,
)

/** Looks up a move timeline by normalized move name. */
fun interface MoveTimelineCatalog {
    fun get(move: String): MasteryTimeline?
}

/** Reads move timelines from a `data/cobblemon/action_effects/moves` folder. */
class PathMoveTimelineCatalog(private val root: Path) : MoveTimelineCatalog {
    private val cache = HashMap<String, MasteryTimeline?>()

    @Synchronized
    override fun get(move: String): MasteryTimeline? {
        if (move in cache) return cache[move]
        return readMove(move).also { cache[move] = it }
    }

    private fun readMove(move: String): MasteryTimeline? {
        if (move.isEmpty() || !SAFE_NAME.matches(move)) return null
        val file = root.resolve("$move.json")
        if (!Files.isRegularFile(file)) return null
        return try {
            val json = Files.newBufferedReader(file).use { JsonParser.parseReader(it) as? JsonObject } ?: return null
            val animations = ArrayList<String>()
            val actor = ArrayList<ResourceLocation>()
            val target = ArrayList<ResourceLocation>()
            collectSteps(json.get("timeline"), animations, actor, target)
            MasteryTimeline(animations.distinct(), actor.distinct(), target.distinct())
        } catch (e: IOException) {
            null
        } catch (e: RuntimeException) {
            null
        }
    }

    private fun collectSteps(
        element: JsonElement?,
        animations: MutableList<String>,
        actor: MutableList<ResourceLocation>,
        target: MutableList<ResourceLocation>,
    ) {
        when {
            element == null -> return

            element.isJsonArray -> element.asJsonArray.forEach { collectSteps(it, animations, actor, target) }

            element.isJsonObject -> {
                val obj = element.asJsonObject
                val onTarget = obj.get("entityCondition")?.takeIf { it.isJsonPrimitive }?.asString?.contains("is_user == false") == true
                when (obj.get("type")?.takeIf { it.isJsonPrimitive }?.asString) {
                    "animation" -> if (!onTarget) {
                        obj.getAsJsonArray("animation")?.forEach { item ->
                            if (item.isJsonPrimitive && item.asJsonPrimitive.isString) animations += item.asString
                        }
                    }

                    "entity_particles" -> {
                        val id = obj.get("effect")?.takeIf { it.isJsonPrimitive }?.asString?.let(ResourceLocation::tryParse)
                        if (id != null) if (onTarget) target += id else actor += id
                    }

                    else -> {
                        collectSteps(obj.get("steps"), animations, actor, target)
                        collectSteps(obj.get("timeline"), animations, actor, target)
                    }
                }
            }
        }
    }

    private companion object {
        val SAFE_NAME = Regex("""[a-z0-9_]+""")
    }
}

/** Checks that a particle id and every particle it spawns exist in [root] with no sound event or argument-less `math.random()`. */
class SilentParticleCatalog(private val root: Path) : ParticleCatalog {
    private class Entry(val spawns: Set<ResourceLocation>, val unsafe: Boolean)

    private val entries: Map<ResourceLocation, Entry> by lazy(::load)
    private val answers = HashMap<ResourceLocation, Boolean>()

    @Synchronized
    override fun contains(id: ResourceLocation): Boolean = answers.getOrPut(id) { check(id) }

    private fun check(rootId: ResourceLocation): Boolean {
        if (rootId !in entries) return false
        val seen = HashSet<ResourceLocation>()
        val queue = ArrayDeque<ResourceLocation>()
        queue += rootId
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!seen.add(current)) continue
            val entry = entries[current] ?: continue
            if (entry.unsafe) return false
            queue += entry.spawns
        }
        return true
    }

    private fun load(): Map<ResourceLocation, Entry> {
        if (!Files.isDirectory(root)) return emptyMap()
        val files = Files.walk(root).use { all -> all.filter { it.fileName.toString().endsWith(SUFFIX) }.toList() }
        val result = HashMap<ResourceLocation, Entry>(files.size)
        for (file in files) {
            try {
                val text = Files.readString(file)
                val effect = (JsonParser.parseString(text) as? JsonObject)?.getAsJsonObject("particle_effect") ?: continue
                val rawId = effect.getAsJsonObject("description")?.get("identifier")?.takeIf { it.isJsonPrimitive }?.asString ?: continue
                val id = ResourceLocation.tryParse(rawId) ?: continue
                val spawns = HashSet<ResourceLocation>()
                val events = effect.get("events")
                events?.let { collectSpawns(it, spawns) }
                val sound = events?.toString()?.contains("\"sound_effect\"") == true
                val badRandom = NO_ARGUMENT_RANDOM.containsMatchIn(text)
                result[id] = Entry(spawns, sound || badRandom)
            } catch (_: IOException) {
            } catch (_: RuntimeException) {
            }
        }
        return result
    }

    private fun collectSpawns(element: JsonElement, into: MutableSet<ResourceLocation>) {
        when {
            element.isJsonObject -> for ((key, value) in element.asJsonObject.entrySet()) {
                val raw = value.takeIf { key == "particle_effect" && it.isJsonObject }?.asJsonObject?.get("effect")
                    ?.takeIf { it.isJsonPrimitive }?.asString
                raw?.let(ResourceLocation::tryParse)?.let(into::add)
                collectSpawns(value, into)
            }

            element.isJsonArray -> element.asJsonArray.forEach { collectSpawns(it, into) }
        }
    }

    private companion object {
        const val SUFFIX = ".particle.json"
        val NO_ARGUMENT_RANDOM = Regex("""math\.random\(\s*\)""", RegexOption.IGNORE_CASE)
    }
}

/** Move timelines from the loaded Cobblemon jar. */
object JarMoveTimelineCatalog : MoveTimelineCatalog {
    private val warnings = OnceLogger()

    private val catalog: PathMoveTimelineCatalog? by lazy {
        val root = ModList.get().getModFileById("cobblemon")?.file?.findResource("data", "cobblemon", "action_effects", "moves")
        if (root != null && Files.isDirectory(root)) {
            PathMoveTimelineCatalog(root)
        } else {
            warnings.warn(FOLDER) { "Cobblemon's move timeline files are missing, so Move Mastery uses role effects" }
            null
        }
    }

    override fun get(move: String): MasteryTimeline? = catalog?.get(move)

    private const val FOLDER = "data/cobblemon/action_effects/moves"
}

/** Silent particle catalog from the loaded Cobblemon jar. */
object JarSilentParticleCatalog : ParticleCatalog {
    private val warnings = OnceLogger()

    private val catalog: SilentParticleCatalog? by lazy {
        val root = ModList.get().getModFileById("cobblemon")?.file?.findResource("assets", "cobblemon", "bedrock", "particles")
        if (root != null && Files.isDirectory(root)) {
            SilentParticleCatalog(root)
        } else {
            warnings.warn(FOLDER) { "Cobblemon's particle files are missing, so Move Mastery skips move particles" }
            null
        }
    }

    override fun contains(id: ResourceLocation): Boolean = catalog?.contains(id) ?: false

    private const val FOLDER = "assets/cobblemon/bedrock/particles"
}

/** The chosen silent work effect and animation for one Working worker (CD-PRG-06). */
data class ResolvedWorkEffect(val effect: EffectDefinition, val animation: WorkAnimation?)

/** Resolves silent Move Mastery animations and particles with fallback to the role's configured effect (CD-PRG-06). */
object MasteryEffect {
    fun resolve(
        species: ResourceLocation,
        aspects: Set<String>,
        masteryMoves: List<String>,
        fallback: EffectDefinition,
        animations: AnimationCatalog,
        timelines: MoveTimelineCatalog,
        silentParticles: ParticleCatalog,
    ): ResolvedWorkEffect {
        for (move in masteryMoves) {
            val timeline = timelines.get(move) ?: continue
            val actor = timeline.actorParticles.filter(silentParticles::contains)
            val target = timeline.targetParticles.filter(silentParticles::contains)
            val animation = animations.first(species, aspects, timeline.animations)
            if (animation == null && actor.isEmpty() && target.isEmpty()) continue
            val chosenEffect = EffectDefinition(
                loopTicks = fallback.loopTicks,
                idleTicks = fallback.idleTicks,
                animations = timeline.animations,
                actorParticles = actor,
                targetParticles = target,
            )
            return ResolvedWorkEffect(chosenEffect, animation ?: animations.first(species, aspects, fallback.animations))
        }
        return ResolvedWorkEffect(fallback, animations.first(species, aspects, fallback.animations))
    }
}
