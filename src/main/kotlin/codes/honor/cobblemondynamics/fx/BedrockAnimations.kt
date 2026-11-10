package codes.honor.cobblemondynamics.fx

import codes.honor.cobblemondynamics.OnceLogger
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.resources.ResourceLocation
import net.neoforged.fml.ModList
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.ceil

/** The animation that a worker's model plays for its role: its name in the role's list, and its length (CD-FX-01, CD-FX-03). */
data class WorkAnimation(val name: String, val lengthTicks: Int) {
    companion object {
        /** The whole ticks that an animation of [seconds] needs to end. */
        fun ticks(seconds: Double): Int = ceil(seconds * TICKS_PER_SECOND - 1e-9).toInt()

        private const val TICKS_PER_SECOND = 20
    }
}

/** Which animation of a list the model of a Pokémon plays. */
fun interface AnimationCatalog {
    /** The first of [names] that the model of [species] with [aspects] plays, or null when it plays none of them. */
    fun first(species: ResourceLocation, aspects: Set<String>, names: List<String>): WorkAnimation?
}

/**
 * Cobblemon's Pokémon animations as its client picks them, from the client assets under [root], the `bedrock/pokemon` folder of the
 * Cobblemon jar. The model variations of a species name its poser. The poser maps an animation name to an animation of a group, and
 * the group's file gives the length of that animation. A server has no client resources, so this reads each file once, on first use.
 */
class BedrockAnimations(private val root: Path) : AnimationCatalog {
    /** Where an animation name of a poser points: `animation.<group>.<animation>` in the file `<group>.animation.json`. */
    data class AnimationRef(val group: String, val animation: String)

    private class Variation(val aspects: Set<String>, val poser: ResourceLocation)

    private data class Query(val species: ResourceLocation, val aspects: Set<String>, val names: List<String>)

    private val variations: Map<ResourceLocation, List<Variation>> by lazy(::readVariations)
    private val poserFiles: Map<String, Path> by lazy { files("posers", ".json") }
    private val groupFiles: Map<String, Path> by lazy { files("animations", ".animation.json") }
    private val posers = HashMap<String, Map<String, AnimationRef>>()
    private val groups = HashMap<String, Map<String, Double>>()
    private val answers = HashMap<Query, WorkAnimation?>()

    @Synchronized
    override fun first(species: ResourceLocation, aspects: Set<String>, names: List<String>): WorkAnimation? {
        val query = Query(species, aspects, names)
        if (query in answers) return answers[query]
        return resolve(species, aspects, names).also { answers[query] = it }
    }

    private fun resolve(species: ResourceLocation, aspects: Set<String>, names: List<String>): WorkAnimation? {
        val poser = poserOf(species, aspects) ?: return null
        for (name in names) {
            val ref = animationOf(poser, name) ?: continue
            val seconds = groupOf(ref.group)["animation.${ref.group}.${ref.animation}"] ?: continue
            if (seconds > 0) return WorkAnimation(name, WorkAnimation.ticks(seconds))
        }
        return null
    }

    /**
     * The poser of the last variation that fits, as Cobblemon's client picks it: a variation fits when the Pokémon has all of its
     * aspects. A variation with a MoLang `condition` never fits here, because only the client can run it.
     */
    private fun poserOf(species: ResourceLocation, aspects: Set<String>): ResourceLocation? =
        variations[species]?.lastOrNull { aspects.containsAll(it.aspects) }?.poser

    /**
     * A JSON poser's own entry for [name]. A poser with no JSON file is one of Cobblemon's Kotlin models, which map a name to the
     * animation of that name in the group named after the poser.
     */
    private fun animationOf(poser: ResourceLocation, name: String): AnimationRef? {
        if (poser.namespace != NAMESPACE) return null
        val file = poserFiles[poser.path] ?: return AnimationRef(poser.path, name)
        return posers.getOrPut(poser.path) { readPoser(file) }[name]
    }

    private fun groupOf(group: String): Map<String, Double> = groups.getOrPut(group) { groupFiles[group]?.let(::readGroup).orEmpty() }

    /** The variations of each species, in Cobblemon's order: by each file's `order`, then by path, each file in its own order. */
    private fun readVariations(): Map<ResourceLocation, List<Variation>> {
        class VariationSet(val species: ResourceLocation, val order: Int, val path: String, val variations: List<Variation>)
        val sets = files("resolvers", ".json").values.mapNotNull { path ->
            read(path) { file ->
                val species = (file.get("species") ?: file.get("name"))?.asString?.let(ResourceLocation::tryParse) ?: return@read null
                val variations = file.getAsJsonArray("variations")?.mapNotNull { (it as? JsonObject)?.let(::variationOf) }.orEmpty()
                VariationSet(species, file.get("order")?.asInt ?: 0, path.toString(), variations)
            }
        }
        val ordered = sets.sortedWith(compareBy<VariationSet>({ it.order }, { it.path }))
        return ordered.groupBy(VariationSet::species, VariationSet::variations).mapValues { (_, lists) -> lists.flatten() }
    }

    /** Only a variation that names a poser and has no condition can pick the poser. */
    private fun variationOf(variation: JsonObject): Variation? {
        if (variation.has("condition")) return null
        val poser = variation.get("poser")?.asString?.let(ResourceLocation::tryParse) ?: return null
        val aspects = variation.getAsJsonArray("aspects")?.mapTo(HashSet()) { it.asString }.orEmpty()
        return Variation(aspects, poser)
    }

    /** A poser's `animations`: names to MoLang such as `q.bedrock_primary('charmander', 'special', q.curve('symmetrical_wide'))`. */
    private fun readPoser(file: Path): Map<String, AnimationRef> = read(file) { poser ->
        poser.getAsJsonObject("animations")?.entrySet()?.mapNotNull { (name, value) ->
            val match = value.takeIf { it.isJsonPrimitive }?.asString?.let(BEDROCK_ANIMATION::find)
            match?.let { name to AnimationRef(it.groupValues[1], it.groupValues[2]) }
        }?.toMap()
    }.orEmpty()

    /** The `animation_length` of each animation in a group file that has one. */
    private fun readGroup(file: Path): Map<String, Double> = read(file) { group ->
        group.getAsJsonObject("animations")?.entrySet()?.mapNotNull { (name, value) ->
            val length = (value as? JsonObject)?.get("animation_length")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            length?.let { name to it.asDouble }
        }?.toMap()
    }.orEmpty()

    /** Each file under [folder] that ends with [suffix], by its name without the suffix, as Cobblemon names posers and groups. */
    private fun files(folder: String, suffix: String): Map<String, Path> {
        val dir = root.resolve(folder)
        if (!Files.isDirectory(dir)) return emptyMap()
        val paths = Files.walk(dir).use { all -> all.filter { it.fileName.toString().endsWith(suffix) }.toList() }
        return paths.associateBy { it.fileName.toString().removeSuffix(suffix) }
    }

    /** [parse] of the JSON object in [file]. A file that does not read, or holds other JSON, counts as missing, so a broken asset never stops the server tick. */
    private fun <T> read(file: Path, parse: (JsonObject) -> T?): T? = try {
        Files.newBufferedReader(file).use { reader -> (JsonParser.parseReader(reader) as? JsonObject)?.let(parse) }
    } catch (e: IOException) {
        null
    } catch (e: RuntimeException) {
        null
    }

    private companion object {
        const val NAMESPACE = "cobblemon"

        /** The group and the animation of Cobblemon's `q.bedrock`, `q.bedrock_primary` and `q.bedrock_stateful` functions. */
        val BEDROCK_ANIMATION = Regex("""q\.bedrock(?:_primary|_stateful)?\(\s*'([^']+)'\s*,\s*'([^']+)'""")
    }
}

/** The animations of the loaded Cobblemon. Without its files no model is known to have one, so workers show their particles only. */
object JarAnimationCatalog : AnimationCatalog {
    private val warnings = OnceLogger()

    private val animations: BedrockAnimations? by lazy {
        val root = ModList.get().getModFileById("cobblemon")?.file?.findResource("assets", "cobblemon", "bedrock", "pokemon")
        if (root != null && Files.isDirectory(root)) {
            BedrockAnimations(root)
        } else {
            warnings.warn(NAMESPACE_FOLDER) { "Cobblemon's Pokémon animation files are missing, so workers play no work animation" }
            null
        }
    }

    override fun first(species: ResourceLocation, aspects: Set<String>, names: List<String>): WorkAnimation? =
        animations?.first(species, aspects, names)

    private const val NAMESPACE_FOLDER = "assets/cobblemon/bedrock/pokemon"
}
