package codes.honor.cobblemondynamics.fx

import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path

/** The Cobblemon jar on the test classpath, opened as a file system, for tests that read the client assets that it ships. */
object CobblemonJar {
    private const val PROBE = "assets/cobblemon/bedrock/particles/generic/heal_circles.particle.json"

    private val fileSystem: FileSystem by lazy {
        val url = javaClass.classLoader.getResource(PROBE) ?: error("no Cobblemon jar with client assets on the test classpath")
        // A resource in a jar has the URL jar:file:/<jar>!/<entry>.
        val jar = url.toString().removePrefix("jar:").substringBefore("!/")
        FileSystems.newFileSystem(Path.of(URI(jar)))
    }

    /** `assets/cobblemon/bedrock`, which holds the particles and the Pokémon models, posers and animations. */
    val bedrock: Path get() = fileSystem.getPath("/assets/cobblemon/bedrock")
}
