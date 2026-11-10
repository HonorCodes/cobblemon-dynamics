package codes.honor.cobblemondynamics.build

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.claim.ClaimKind
import codes.honor.cobblemondynamics.command.StaminaReport
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.stamina.RecoveryRule
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** Static gates over the main source set and `en_us.json` (CD-UI-11, CD-UI-12, CD-PERF-05). */
class LangTest {
    private val root = File(System.getProperty("cd.projectDir"))
    private val assets = File(root, "src/main/resources/assets/cobblemon_dynamics")

    private val lang: Map<String, String> = JsonParser.parseString(File(assets, "lang/en_us.json").readText())
        .asJsonObject.entrySet().associate { it.key to it.value.asString }

    /** A main source file, with its path from the root package, for example `engine/WorkEngine.kt`. */
    private class Source(val path: String, val text: String)

    private val sources: List<Source> = listOf("src/main/kotlin", "src/main/java").flatMap { dir ->
        val base = File(root, "$dir/$PACKAGE_DIR")
        base.walk().filter { it.isFile && it.extension in setOf("kt", "java") }
            .map { Source(it.relativeTo(base).invariantSeparatorsPath, it.readText()) }.toList()
    }

    /** Every `const val NAME = "value"` of the main source set, by name. */
    private val constants: Map<String, List<String>> = sources
        .flatMap { s -> CONSTANT.findAll(s.text).map { it.groupValues[1] to it.groupValues[2] } }
        .groupBy({ it.first }, { it.second })

    /** The suffixes of each key family that the code builds as `"$PREFIX.<suffix>"`. A new family must be added here. */
    private val families: Map<String, List<String>> = mapOf(
        "cobblemon_dynamics.state" to (WorkerStatus.entries + null).map { StaminaReport.stateKey(it).substringAfterLast('.') },
        "cobblemon_dynamics.rule" to RecoveryRule.entries.map { StaminaReport.ruleKey(it).substringAfterLast('.') },
        "cobblemon_dynamics.command.claims.kind" to ClaimKind.entries.map { it.name.lowercase() },
        // LeashResets names each cause of a lost binding with a string constant. Its other string constants are lang keys.
        "cobblemon_dynamics.binding.reason" to source("leash/LeashResets.kt").let { s ->
            CONSTANT.findAll(s.text).map { it.groupValues[2] }.filter { REASON_ID.matches(it) }.toList()
        },
    )

    private fun source(path: String): Source = sources.single { it.path == path }

    private fun missing(keys: Iterable<String>): List<String> = keys.filterNot(lang::containsKey).distinct().sorted()

    /** The prefixes of every `"$NAME.` and `"${Owner.NAME}.` template in [text], resolved through the string constants. */
    private fun templatePrefixes(text: String): List<String> = TEMPLATE.findAll(text).flatMap { match ->
        constants[match.groupValues[1]].orEmpty()
    }.toList()

    @Test
    fun `CD-UI-12 every lang key string in code exists in en_us, or names a key family`() {
        val prefixes = sources.flatMap { templatePrefixes(it.text) }.toSet()
        val literals = sources.flatMap { s -> KEY_LITERAL.findAll(s.text).map { it.groupValues[1] } }.toSet()
        val unknown = literals.filterNot { it in lang || it in prefixes }.sorted()
        assertTrue(unknown.isEmpty(), "lang keys in code that en_us lacks: $unknown")
    }

    @Test
    fun `CD-UI-12 every key that the code builds exists in en_us`() {
        val prefixes = sources.flatMap { templatePrefixes(it.text) }.filter { it == MOD_ID || it.startsWith(KEY_ROOT) }.toSet()
        assertEquals(families.keys, prefixes, "the key families that the code builds")
        for ((prefix, suffixes) in families) assertTrue(suffixes.isNotEmpty(), "no suffixes for $prefix")
        val gaps = missing(families.flatMap { (prefix, suffixes) -> suffixes.map { "$prefix.$it" } })
        assertTrue(gaps.isEmpty(), "built keys that en_us lacks: $gaps")
    }

    @Test
    fun `CD-UI-12 every Component_translatable key literal exists in en_us`() {
        val keys = sources.flatMap { s -> TRANSLATABLE.findAll(s.text).map { it.groupValues[1] } }
        val templates = keys.filter { '$' in it }
        val gaps = missing(keys - templates.toSet())
        assertTrue(gaps.isEmpty(), "Component.translatable keys that en_us lacks: $gaps")
        val prefixes = sources.flatMap { templatePrefixes(it.text) }.toSet()
        for (template in templates) {
            val name = TEMPLATE.find("\"$template")?.groupValues?.get(1)
            assertTrue(constants[name].orEmpty().any { it in prefixes }, "a translatable key that no key family covers: $template")
        }
    }

    @Test
    fun `CD-UI-12 every item, block and entity type has a name in en_us`() {
        val items = File(assets, "models/item").listFiles().orEmpty().map { it.nameWithoutExtension }
        assertTrue(items.isNotEmpty(), "no item models")
        val unnamedItems = items.filterNot { "item.$KEY_ROOT$it" in lang || "block.$KEY_ROOT$it" in lang }
        assertTrue(unnamedItems.isEmpty(), "items with no name: $unnamedItems")
        val blocks = File(assets, "blockstates").listFiles().orEmpty().map { "block.$KEY_ROOT${it.nameWithoutExtension}" }
        assertTrue(blocks.isNotEmpty(), "no block states")
        assertTrue(missing(blocks).isEmpty(), "blocks with no name: ${missing(blocks)}")
        val registered = ENTITY_TYPE.findAll(source("registry/DynamicsEntities.kt").text).map { it.groupValues[1] }.toList()
        val entities = registered.map { "entity.$KEY_ROOT$it" }
        assertTrue(entities.isNotEmpty(), "no entity types")
        assertTrue(missing(entities).isEmpty(), "entity types with no name: ${missing(entities)}")
    }

    @Test
    fun `CD-UI-12 no main source file builds a literal text component`() {
        val offenders = sources.filter { LITERAL_COMPONENT.containsMatchIn(it.text) }.map { it.path }
        assertTrue(offenders.isEmpty(), "Component.literal or Component.nullToEmpty in: $offenders")
    }

    @Test
    fun `CD-UI-11 every en_us value that names the stat reads Work Stamina`() {
        val bare = lang.filter { (_, value) ->
            STAT.findAll(value).any { it.value != "Stamina" || !value.regionMatches(it.range.first - WORK.length, WORK, 0, WORK.length) }
        }
        assertTrue(lang.values.any { "Work Stamina" in it }, "no en_us value names Work Stamina")
        assertTrue(bare.isEmpty(), "values that name the stat without \"Work Stamina\": $bare")
    }

    @Test
    fun `CD-PERF-05 per-tick packages log only through OnceLogger`() {
        val perTick = sources.filter { s -> PER_TICK_DIRS.any { s.path.startsWith(it) } }
        assertTrue(perTick.any { it.path == "engine/WorkEngine.kt" }, "the scan found no engine sources")
        val offenders = perTick.flatMap { s ->
            DIRECT_LOGGING.flatMap { rule -> rule.findAll(s.text).map { "${s.path}: ${it.value.trim()}" } }
        }
        assertTrue(offenders.isEmpty(), "logging that bypasses OnceLogger: $offenders")
    }

    private companion object {
        const val PACKAGE_DIR = "codes/honor/cobblemondynamics"
        const val KEY_ROOT = "cobblemon_dynamics."
        const val WORK = "Work "

        /** The packages whose code runs on every server tick. */
        val PER_TICK_DIRS = listOf("engine/", "handler/", "fx/")

        val CONSTANT = Regex("""const\s+val\s+([A-Z][A-Z0-9_]*)\s*(?::\s*String\s*)?=\s*"([^"$]*)"""")
        val KEY_LITERAL = Regex(""""(cobblemon_dynamics\.[a-z0-9_.]+)"""")
        val TEMPLATE = Regex(""""\$\{?(?:[A-Za-z_][A-Za-z0-9_]*\.)*([A-Z][A-Z0-9_]*)\}?\.""")
        val TRANSLATABLE = Regex("""Component\s*\.\s*translatable\w*\s*\(\s*"([^"]*)"""")
        val LITERAL_COMPONENT = Regex("""\bComponent\s*\.\s*(literal|nullToEmpty)\s*\(""")
        val ENTITY_TYPE = Regex("""REGISTER\s*\.\s*register\s*\(\s*"([a-z0-9_]+)"""")
        val REASON_ID = Regex("""[a-z][a-z0-9_]*""")
        val STAT = Regex("""(?i)\bstamina\b""")

        /** A logger call above DEBUG, an own logger, or the mod logger handed on for such a call. */
        val DIRECT_LOGGING = listOf(
            Regex("""\bLOGGER\s*\.\s*(info|warn|error|atInfo|atWarn|atError|log)\s*\("""),
            Regex("""\bgetLogger\s*\("""),
            Regex("""\bCobblemonDynamics\s*\.\s*LOGGER\b(?!\s*\.\s*(debug|trace)\s*\()"""),
        )
    }
}
