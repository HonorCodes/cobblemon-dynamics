package codes.honor.cobblemondynamics.build

import codes.honor.cobblemondynamics.compat.OptionalMod
import codes.honor.cobblemondynamics.compat.OptionalMods
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class CompatImportsTest {
    private val root = File(System.getProperty("cd.projectDir"))

    private fun mainSources(): List<File> = File(root, "src/main").walk().filter { it.extension == "kt" || it.extension == "java" }.toList()

    /** True when [path] is in `compat/<name>/` or `mixin/compat/<name>/` of the main source set. */
    private fun inCompat(path: String, name: String): Boolean =
        "/cobblemondynamics/compat/$name/" in path || "/cobblemondynamics/mixin/compat/$name/" in path

    /** The compat packages that may name the dotted [prefix]: those of each optional mod whose packages it covers or holds. */
    private fun compatOf(prefix: String): List<String> = OptionalMods.ALL.filter { mod ->
        mod.packages.map { it.replace('/', '.') }.any { it.startsWith(prefix) || prefix.startsWith(it) }
    }.mapNotNull { it.compat }

    @Test
    fun `CD-QWK-33 only the compat package of a claim mod names it, and only behind isLoaded`() {
        val sources = File(root, "src/main").walk().filter { it.extension == "kt" || it.extension == "java" }
        for (file in sources) {
            val text = file.readText()
            val path = file.invariantSeparatorsPath
            if ("dev.ftb." in text) assertTrue("/compat/ftbchunks/" in path, "$path names FTB")
            if ("xaero.pac." in text) assertTrue("/compat/opac/" in path, "$path names Open Parties and Claims")
        }
        val mod = File(root, "src/main/kotlin/codes/honor/cobblemondynamics/CobblemonDynamics.kt").readText()
        assertTrue("if (ModList.get().isLoaded(\"ftbchunks\")) FtbChunksCompat.register()" in mod)
        assertTrue("if (ModList.get().isLoaded(\"openpartiesandclaims\")) OpacCompat.register()" in mod)
    }

    @Test
    fun `CD-LND-13 only compat-minecolonies names MineColonies, and only behind isLoaded`() {
        val sources = File(root, "src/main").walk().filter { it.extension == "kt" || it.extension == "java" }
        val naming = sources.filter { file -> file.readText().let { "com.minecolonies." in it || "com.ldtteam." in it } }.toList()
        for (file in naming) {
            val path = file.invariantSeparatorsPath
            assertTrue("/compat/minecolonies/" in path, "$path names MineColonies or a library of LDTTeam")
        }
        assertTrue(naming.isNotEmpty(), "no file names MineColonies, so compat/minecolonies/ is missing")
        val mod = File(root, "src/main/kotlin/codes/honor/cobblemondynamics/CobblemonDynamics.kt").readText()
        assertTrue("if (ModList.get().isLoaded(\"minecolonies\")) MineColoniesCompat.register()" in mod)
    }

    /** ADR-0015: the crop adapters read vanilla classes, Cobblemon's classes, block state properties and tags only. */
    @Test
    fun `CD-CRP-19 no main source names a crop mod`() {
        val prefixes = listOf(
            "vectorwing.",
            "com.pam.",
            "net.mehvahdjukaar.",
            "blusunrize.",
            "com.aetherteam.",
            "io.github.razordevs.",
            "net.potionstudios.",
            "quek.undergarden.",
            "com.hollingsworth.",
            "com.klikli_dev.",
            "net.regions_unexplored.",
            "ianm1647.",
            "com.jorgaomc.",
        )
        // CD-OPT-05: the compat packages of a mod of ADR-0016 are the only exceptions, such as compat/immersiveengineering/.
        val exceptions = prefixes.associateWith(::compatOf)
        val sources = mainSources()
        assertTrue(sources.isNotEmpty())
        for (file in sources) {
            val text = file.readText()
            val path = file.invariantSeparatorsPath
            for (prefix in prefixes) {
                if (exceptions.getValue(prefix).none { inCompat(path, it) }) assertFalse(prefix in text, "$path names $prefix")
            }
        }
    }

    /**
     * A place that names a package belongs to the mod with the longest matching prefix, so `mekanism.generators.` names Mekanism
     * Generators and not Mekanism. A prefix counts only where no letter, digit or dot comes before it, so the compat package
     * `codes.honor.cobblemondynamics.compat.mekanism.` does not name Mekanism.
     */
    private fun namedMods(text: String): Map<String, OptionalMod> {
        val owners = HashMap<Int, Pair<String, OptionalMod>>()
        for (mod in OptionalMods.ALL) {
            for (prefix in mod.packages.map { it.replace('/', '.') }) {
                for (match in Regex("""(?<![\w.])""" + Regex.escape(prefix)).findAll(text)) {
                    val held = owners[match.range.first]
                    if (held == null || prefix.length > held.first.length) owners[match.range.first] = prefix to mod
                }
            }
        }
        return owners.values.associate { it }
    }

    /**
     * The one exception to CD-OPT-05: a compat package whose own mod cannot load without another mod may name that mod's classes.
     * Mekanism Generators 10.7.19.85 declares Mekanism `[10.7.19]` as a required dependency in its `neoforge.mods.toml`, so
     * `compat/mekanismgenerators/` loads only where Mekanism's classes exist, and the upper blocks of its Wind Generator are
     * Mekanism's own `TileEntityBoundingBlock` (CD-AIR-04). The other way stays refused: `compat/mekanism/` never names Mekanism
     * Generators, which a Mekanism pack can lack.
     */
    private val requiredBy = mapOf("mekanism" to listOf("mekanismgenerators"))

    @Test
    fun `CD-OPT-05 CD-CRY-05 only the compat packages of an optional mod name its classes`() {
        val sources = mainSources()
        assertTrue(sources.isNotEmpty())
        for (file in sources) {
            val path = file.invariantSeparatorsPath
            for ((prefix, mod) in namedMods(file.readText())) {
                val allowed = listOfNotNull(mod.compat) + requiredBy[mod.modId].orEmpty()
                assertTrue(allowed.any { inCompat(path, it) }, "$path names $prefix of ${mod.modId} outside its compat packages")
            }
        }
        // The scan finds the one compat package of ADR-0016 that 1.1 already has.
        val apothic = File(root, "src/main/kotlin/codes/honor/cobblemondynamics/compat/apothic/ApothicCompat.kt").readText()
        assertTrue(namedMods(apothic).values.any { it.modId == "apothic_enchanting" }, "the scan does not see Apothic Enchanting")
    }

    @Test
    fun `CD-OPT-06 each compat package of an optional mod registers only behind isLoaded`() {
        val mod = File(root, "src/main/kotlin/codes/honor/cobblemondynamics/CobblemonDynamics.kt").readText()
        val packages = OptionalMods.ALL.filter { it.compat != null }
            .filter { File(root, "src/main/kotlin/codes/honor/cobblemondynamics/compat/${it.compat}").isDirectory }
        assertTrue(packages.isNotEmpty())
        for (optional in packages) {
            val guard = "if (ModList.get().isLoaded(\"${optional.modId}\"))"
            assertTrue(guard in mod, "compat/${optional.compat}/ registers without its guard")
        }
    }

    /**
     * CD-OPT-10: the initializer of a Kotlin `object`, of its `init` block and of a top-level property runs where the object or its
     * file is first used. For a compat package that is the mod's constructor, outside the engine's guard, so a renamed class or field
     * of another mod there would stop the game at load. Such a property reads the class `by lazy`, or a method reads it.
     */
    @Test
    fun `CD-OPT-10 no object or top-level property of a compat package reads another mod's class in its initializer`() {
        val files = File(root, "src/main/kotlin/codes/honor/cobblemondynamics/compat").walk().filter { it.extension == "kt" }.toList()
        assertTrue(files.size > 20, "the scan finds no compat package")
        val eager = files.flatMap { file -> eagerReads(file.readText()).map { "${file.name}: $it" } }
        assertTrue(eager.isEmpty(), "initializers that read another mod's class: $eager")
        // The scan sees an eager read of an imported class, an alias and a dotted package, and passes a lazy read, a getter, a method
        // and a type argument, which the JVM erases.
        val sample = """
            import blusunrize.immersiveengineering.api.IEApi
            import mekanism.api.heat.IHeatHandler as Heat
            import net.minecraft.world.level.Level

            val TOP = IEApi.modPreference
            object Sample {
                private val alias = Heat.EMPTY
                val dotted =
                    appeng.api.AECapabilities.CRANKABLE
                val lazyRead by lazy { IEApi.modPreference }
                val getter get() = IEApi.modPreference
                private val grants = HashMap<IEApi, Level>()
                fun read() = IEApi.modPreference
                init {
                    IEApi.prefersItem()
                }
            }
        """.trimIndent()
        assertEquals(listOf("TOP", "alias", "dotted", "init"), eagerReads(sample).map { it.substringBefore(' ') })
    }

    /**
     * The properties of [source] that read another mod's class in their initializer: those at the top level, in an `object` or in a
     * companion object, and each `init` block of an object. ktlint's layout of four spaces per level places each member.
     */
    private fun eagerReads(source: String): List<String> {
        val imported = Regex("""^import ([\w.]+)(?: as (\w+))?$""", RegexOption.MULTILINE).findAll(source)
            .filterNot { match -> OWN_PACKAGES.any { match.groupValues[1].startsWith(it) } }
            .map { match -> match.groupValues[2].ifEmpty { match.groupValues[1].substringAfterLast('.') } }.toSet()
        val dotted = OptionalMods.ALL.flatMap { it.packages }.map { it.replace('/', '.') }.toSet()
        // Each line without its strings and its line comment, so a name in a string or a comment reads nothing.
        val lines = source.lines().map { it.replace(STRING, "\"\"").substringBefore("//") }
        val indent = { line: String -> line.takeWhile { it == ' ' }.length }
        val reads = ArrayList<String>()
        for ((i, line) in lines.withIndex()) {
            val member = MEMBER.matchEntire(line.trim()) ?: continue
            val depth = indent(line)
            val opener = (i - 1 downTo 0).map { lines[it] }.firstOrNull { it.isNotBlank() && indent(it) == depth - INDENT }
            if (depth > 0 && (opener == null || !OBJECT.containsMatchIn(opener))) continue
            val body = (i + 1 until lines.size).takeWhile { lines[it].isBlank() || indent(lines[it]) > depth }
                .joinToString(" ") { lines[it] }
            var code = when {
                member.groupValues[2] == "init" -> body
                " by lazy" in line || "get()" in line || "const " in member.groupValues[1] -> continue
                " = " in line -> line.substringAfter(" = ") + " " + body
                line.trimEnd().endsWith(" =") -> body
                else -> continue
            }
            while (GENERIC.containsMatchIn(code)) code = code.replace(GENERIC, "")
            val named = imported.filter { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(code) } +
                dotted.filter { Regex("""(?<![\w.])${Regex.escape(it)}""").containsMatchIn(code) }
            if (named.isNotEmpty()) reads += "${member.groupValues[3].ifEmpty { "init" }} reads $named"
        }
        return reads
    }

    @Test
    fun `CD-OPT-13 verifyJar refuses each package of an optional mod`() {
        val gradle = File(root, "build.gradle").readText()
        val list = Regex("""def optionalModPackages = \[([^\]]*)]""").find(gradle)?.groupValues?.get(1)
            ?: error("build.gradle has no optionalModPackages list")
        val refused = Regex("'([^']+)'").findAll(list).map { it.groupValues[1] }.toSet()
        for (mod in OptionalMods.ALL) {
            for (prefix in mod.packages) assertTrue(prefix in refused, "verifyJar does not refuse $prefix of ${mod.modId}")
        }
    }

    @Test
    fun `CD-MUS-07 only the compat-apothic packages name Apothic Enchanting, and only with the mod`() {
        val sources = File(root, "src/main").walk().filter { it.extension == "kt" || it.extension == "java" }
        val naming = sources.filter { "dev.shadowsoffire." in it.readText() }.toList()
        for (file in naming) {
            val path = file.invariantSeparatorsPath
            assertTrue("/compat/apothic/" in path, "$path names Apothic Enchanting")
        }
        assertTrue(naming.any { "/mixin/compat/apothic/" in it.invariantSeparatorsPath }, "mixin/compat/apothic/ has no stats hook")
        val plugin = File(root, "src/main/java/codes/honor/cobblemondynamics/mixin/MixinPlugin.java").readText()
        assertTrue("BASE + \"compat.apothic.\", \"apothic_enchanting\"" in plugin, "MixinPlugin applies the stats hook without the mod")
        val mod = File(root, "src/main/kotlin/codes/honor/cobblemondynamics/CobblemonDynamics.kt").readText()
        assertTrue("if (ModList.get().isLoaded(\"apothic_enchanting\")) ApothicCompat.register(modBus)" in mod)
    }

    private companion object {
        const val INDENT = 4

        /** The packages that every server loads, which an initializer may read: this mod, the platform and their libraries. */
        val OWN_PACKAGES = listOf(
            "codes.honor.", "net.minecraft.", "net.neoforged.", "kotlin.", "java.", "javax.", "com.mojang.", "org.slf4j.",
            "com.google.", "io.netty.", "it.unimi.", "org.jetbrains.", "com.cobblemon.",
        )

        /** A property or an `init` block: its modifiers, `val`, `var` or `init`, and the property's name. */
        private const val MODIFIERS = """(?:(?:private|internal|public|override|lateinit|const|@JvmField|@Volatile)\s+)*"""
        val MEMBER = Regex("""^($MODIFIERS)(val|var|init)\b\s*(\w*).*""")

        /** A line that opens an object declaration or a companion object, not an object expression. */
        val OBJECT = Regex("""^\s*(?:(?:private|internal|public|data)\s+)*(?:companion\s+)?object\b""")

        val STRING = Regex(""""[^"]*"""")

        /** A type argument, which the JVM erases, so it loads no class. */
        val GENERIC = Regex("<[^<>]*>")
    }
}
