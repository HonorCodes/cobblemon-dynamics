package codes.honor.cobblemondynamics.fx

import codes.honor.cobblemondynamics.OnceLogger
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import net.minecraft.resources.ResourceLocation
import net.neoforged.fml.ModList
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** Whether the loaded Cobblemon has a particle effect with an id. */
fun interface ParticleCatalog {
    fun contains(id: ResourceLocation): Boolean
}

/** Drops the ids that the catalog lacks, with one warning per id. The animation plays either way (CD-FX-11). */
class CheckedParticles(private val catalog: ParticleCatalog, private val warnings: OnceLogger) {
    fun filter(ids: List<ResourceLocation>): List<ResourceLocation> = ids.filter { id ->
        val known = catalog.contains(id)
        if (!known) warnings.warn(id) { "Particle $id is not in the loaded Cobblemon, so work effects play without it" }
        known
    }
}

/**
 * The particle ids in the Cobblemon jar: the `identifier` of each `.particle.json` file under `assets/cobblemon/bedrock/particles`,
 * the files that Cobblemon's client loads. A server has no client resources, so this reads the jar once, on first use.
 */
object JarParticleCatalog : ParticleCatalog {
    private const val SUFFIX = ".particle.json"

    private val warnings = OnceLogger()

    private val ids: Set<ResourceLocation>? by lazy(::load)

    // Without the files nothing is known. The client skips an id that it lacks, so every id goes out.
    override fun contains(id: ResourceLocation): Boolean = ids?.contains(id) ?: true

    private fun load(): Set<ResourceLocation>? {
        val root = ModList.get().getModFileById("cobblemon")?.file?.findResource("assets", "cobblemon", "bedrock", "particles")
        if (root == null || !Files.isDirectory(root)) {
            warnings.warn(SUFFIX) { "Cobblemon's particle files are missing, so work effects cannot check their particle ids" }
            return null
        }
        val files = Files.walk(root).use { paths -> paths.filter { it.fileName.toString().endsWith(SUFFIX) }.toList() }
        return files.mapNotNullTo(HashSet(), ::identifierOf)
    }

    private fun identifierOf(file: Path): ResourceLocation? = try {
        Files.newBufferedReader(file).use { reader ->
            val effect = JsonParser.parseReader(reader).asJsonObject.getAsJsonObject("particle_effect")
            effect?.getAsJsonObject("description")?.get("identifier")?.asString?.let(ResourceLocation::tryParse)
        }
    } catch (e: IOException) {
        null
    } catch (e: JsonParseException) {
        null
    } catch (e: IllegalStateException) {
        null
    }
}
