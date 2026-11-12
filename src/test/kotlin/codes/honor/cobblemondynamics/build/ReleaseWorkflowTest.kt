package codes.honor.cobblemondynamics.build

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.File
import java.util.Properties

/** The rules of `.github/workflows/release.yml`, checked without a tag (CD-REL-04, CD-REL-05, CD-REL-12). */
class ReleaseWorkflowTest {
    private val root = File(System.getProperty("cd.projectDir"))

    private val workflow: Map<*, *> = Yaml(SafeConstructor(LoaderOptions())).load(File(root, ".github/workflows/release.yml").readText())

    private val jobs = workflow["jobs"] as Map<*, *>

    /** The workflow keeps both tag patterns in its `env`, and bash reads them unquoted with `=~`, as a pattern search. */
    private val patterns = workflow["env"] as Map<*, *>
    private val releaseTag = Regex(patterns["RELEASE_TAG"] as String)
    private val publicTag = Regex(patterns["PUBLIC_TAG"] as String)

    private fun job(name: String): Map<*, *> = jobs[name] as? Map<*, *> ?: fail("no job $name")

    private fun steps(job: String): List<Map<*, *>> = (job(job)["steps"] as List<*>).map { it as Map<*, *> }

    private fun step(job: String, id: String): Map<*, *> = steps(job).firstOrNull { it["id"] == id } ?: fail("no step $id in job $job")

    /** The trimmed lines of a step's script. */
    private fun script(job: String, id: String): List<String> = (step(job, id)["run"] as String).lines().map { it.trim() }

    /** The lines of [lines] from the first one that equals the first of [expected], as many as [expected] holds. */
    private fun block(lines: List<String>, expected: List<String>): List<String> =
        lines.dropWhile { it != expected.first() }.take(expected.size)

    private fun inputs(step: Map<*, *>): Map<*, *> = step["with"] as? Map<*, *> ?: fail("step ${step["id"]} has no inputs")

    /** The steps of the publish job that upload the jar with mc-publish, in their order. */
    private fun uploads(): List<Map<*, *>> = steps("publish").filter { it["uses"] == MC_PUBLISH }

    /** Whether an input of mc-publish belongs to one platform, as `modrinth-id` does. */
    private fun isPlatformInput(key: Any?): Boolean = PLATFORMS.any { (key as String).startsWith("$it-") }

    /** Every key and scalar under [node], as text. */
    private fun scalars(node: Any?): Sequence<String> = when (node) {
        is Map<*, *> -> node.entries.asSequence().flatMap { (key, value) -> sequenceOf(key.toString()) + scalars(value) }
        is List<*> -> node.asSequence().flatMap { scalars(it) }
        null -> emptySequence()
        else -> sequenceOf(node.toString())
    }

    @Test
    fun `CD-REL-12 the workflow runs on v tags and on nothing else`() {
        // YAML 1.1, which SnakeYAML reads, takes the key on as the boolean true.
        val on = (workflow["on"] ?: workflow[true]) as Map<*, *>
        assertEquals(mapOf("push" to mapOf("tags" to listOf("v*"))), on)
    }

    @Test
    fun `CD-REL-12 a pre-release suffix makes a release tag that is not public`() {
        for (tag in listOf("v1.0.0-alpha.1", "v1.0.0-beta.1", "v1.0.0-rc.2", "v2.10.3-alpha")) {
            assertTrue(releaseTag.containsMatchIn(tag), "$tag must build a release")
            assertFalse(publicTag.containsMatchIn(tag), "$tag must not be public")
        }
        for (tag in listOf("v1.0.0", "v2.10.30")) {
            assertTrue(releaseTag.containsMatchIn(tag) && publicTag.containsMatchIn(tag), "$tag must be public")
        }
        for (tag in listOf("v1.0", "1.0.0", "v1.0.0+build.1", "v1.0.0-", "vnext", "v1.0.0.1")) {
            assertFalse(releaseTag.containsMatchIn(tag), "$tag must fail the build")
        }
    }

    @Test
    fun `CD-REL-12 only a public tag reaches the publish job`() {
        val tag = script("build", "tag")
        assertTrue("if [[ ! \$TAG =~ \$RELEASE_TAG ]]; then" in tag, "the build must refuse a tag that is not a release tag")
        val public = listOf("public=false", "if [[ \$TAG =~ \$PUBLIC_TAG ]]; then", "public=true", "fi")
        assertEquals(public, tag.dropWhile { it != public.first() }.take(public.size), "public is true only for a public tag")
        assertEquals("\${{ steps.tag.outputs.public }}", (job("build")["outputs"] as Map<*, *>)["public"])
        assertEquals("needs.build.outputs.public == 'true'", job("publish")["if"])
    }

    @Test
    fun `CD-REL-12 only the publish job names the publishing secrets or mc-publish`() {
        val names = listOf("MODRINTH_TOKEN", "CURSEFORGE_TOKEN", "mc-publish")
        for ((name, body) in jobs) {
            val text = scalars(body).joinToString("\n")
            val found = names.filter { it in text }
            if (name == "publish") assertEquals(names, found) else assertTrue(found.isEmpty(), "job $name names $found")
        }
        assertFalse(scalars(workflow["env"]).any { "secrets." in it }, "the workflow env must not read a secret")
    }

    @Test
    fun `CD-REL-12 every release tag gets a GitHub release, marked pre-release unless public`() {
        val release = job("github-release")
        assertNull(release["if"], "the GitHub release must run for every release tag")
        assertEquals("build", release["needs"])
        assertEquals(mapOf("contents" to "write"), release["permissions"])
        assertEquals("\${{ needs.build.outputs.public }}", (step("github-release", "release")["env"] as Map<*, *>)["PUBLIC"])
        val lines = script("github-release", "release")
        val flag = listOf("prerelease=true", "if [[ \$PUBLIC == true ]]; then", "prerelease=false", "fi")
        assertEquals(flag, lines.take(flag.size))
        assertTrue(lines.any { it.startsWith("gh release create \"\$TAG\" release/*.jar") }, "the release must get the jar")
        assertEquals(2, lines.count { "--prerelease=\"\$prerelease\"" in it }, "both the new and an existing release get the flag")
    }

    @Test
    fun `CD-REL-04 the publish job skips a site without its settings, with a notice`() {
        assertEquals(listOf("build", "github-release"), job("publish")["needs"])
        val ids = steps("publish").map { it["id"] }
        for (site in SITES) {
            assertTrue(ids.indexOf("settings") in 0 until ids.indexOf(site), "the settings check must run before the $site upload")
        }
        val settings = mapOf(
            "MODRINTH_TOKEN" to "\${{ secrets.MODRINTH_TOKEN }}",
            "CURSEFORGE_TOKEN" to "\${{ secrets.CURSEFORGE_TOKEN }}",
            "MODRINTH_PROJECT_ID" to "\${{ vars.MODRINTH_PROJECT_ID }}",
            "CURSEFORGE_PROJECT_ID" to "\${{ vars.CURSEFORGE_PROJECT_ID }}",
        )
        assertEquals(settings, step("publish", "settings")["env"], "the check reads the token and the project id of each site")
        val check = script("publish", "settings")
        assertFalse(check.any { it.startsWith("exit") }, "a missing setting must not fail the job: the approver uploads by hand (R69)")
        val sites = listOf(
            "for pair in modrinth:MODRINTH curseforge:CURSEFORGE; do",
            "site=\${pair%%:*}",
            "token=\${pair#*:}_TOKEN",
            "project=\${pair#*:}_PROJECT_ID",
        )
        assertEquals(sites, block(check, sites), "the check must cover both sites")
        val outputs = listOf(
            "if [[ -n \${!token} && -n \${!project} ]]; then",
            "echo \"\$site=true\" >> \"\$GITHUB_OUTPUT\"",
            "else",
            "echo \"\$site=false\" >> \"\$GITHUB_OUTPUT\"",
            "echo \"::notice::No upload to \$site. Set the secret \$token and the variable \$project to publish there.\"",
            "fi",
        )
        assertEquals(outputs, block(check, outputs), "each site gets one output, and a site without its settings gets a notice")
    }

    @Test
    fun `CD-REL-04 each site has its own publish step`() {
        val uploads = uploads()
        assertEquals(SITES, uploads.map { it["id"] }, "each site must have its own mc-publish step")
        for ((site, upload) in SITES.zip(uploads)) {
            assertEquals("steps.settings.outputs.$site == 'true'", upload["if"], "the $site upload must run only with its settings")
            val name = site.uppercase()
            val own = mapOf("$site-id" to "\${{ vars.${name}_PROJECT_ID }}", "$site-token" to "\${{ secrets.${name}_TOKEN }}")
            assertEquals(own, inputs(upload).filterKeys(::isPlatformInput), "the $site upload must pass only its own id and token")
            assertNull(inputs(upload)["github-token"], "the GitHub release job owns the GitHub release")
        }
        val shared = uploads.map { upload -> inputs(upload).filterKeys { !isPlatformInput(it) } }
        assertEquals(shared.first(), shared.last(), "both uploads must pass the same release inputs")
        assertEquals("release/*.jar", shared.first()["files"])
        assertEquals("release", shared.first()["version-type"])
        assertEquals("neoforge", shared.first()["loaders"])
        assertEquals("1.21.1", shared.first()["game-versions"])
    }

    @Test
    fun `CD-REL-05 the release jar and both sites carry MAJOR_MINOR_PATCH+mc1_21_1`() {
        assertTrue("echo \"version=\${TAG#v}\"" in script("build", "tag"), "the version is the tag without its v")
        val build = step("build", "build")
        assertEquals("\${{ steps.tag.outputs.version }}", (build["env"] as Map<*, *>)["VERSION"])
        assertEquals("./gradlew build -Prelease_version=\"\$VERSION\" --stacktrace", build["run"])
        val gradle = File(root, "build.gradle").readText()
        assertTrue("version = \"\${project.findProperty('release_version') ?: mod_version}+mc\${minecraft_version}\"" in gradle)
        val properties = Properties().apply { File(root, "gradle.properties").reader().use(::load) }
        assertEquals("1.21.1", properties.getProperty("minecraft_version"))
        val jar = script("build", "jar").joinToString("\n")
        assertTrue("grep -qFx \"version=\\\"\$VERSION+mc1.21.1\\\"\"" in jar, "the build must read the version in the jar's mods.toml")
        val version = "\${{ needs.build.outputs.version }}+mc1.21.1"
        assertEquals(listOf(version, version), uploads().map { inputs(it)["version"] }, "both sites must get the version of the jar")
    }

    /** CD-ART-12: a public release ships only art that the approver approved at its current source. */
    @Test
    fun `CD-ART-12 a public tag checks the approver's approval of every asset before it publishes`() {
        val art = steps("build").firstOrNull { (it["run"] as? String)?.contains("scripts/art.py check --release") == true }
            ?: fail("the build job has no art check")
        assertEquals("steps.tag.outputs.public == 'true'", art["if"])
    }

    private companion object {
        /** Kir-Antipov/mc-publish moved to this name. Its current major is 3. */
        const val MC_PUBLISH = "Kira-NT/mc-publish@v3"

        /** The sites of the publish job, in the order of their upload steps. */
        val SITES = listOf("modrinth", "curseforge")

        /** The prefixes of the mc-publish inputs that belong to one platform. */
        val PLATFORMS = SITES + "github"
    }
}
