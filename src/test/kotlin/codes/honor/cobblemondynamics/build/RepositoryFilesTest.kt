package codes.honor.cobblemondynamics.build

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class RepositoryFilesTest {
    private val root = File(System.getProperty("cd.projectDir"))

    @Test
    fun `CD-REL-06 LICENSE holds the MPL-2_0 text`() {
        assertTrue(File(root, "LICENSE").readText().contains("Mozilla Public License Version 2.0"))
    }

    @Test
    fun `CD-REL-11 README states that the mod is not affiliated`() {
        val readme = File(root, "README.md").readText()
        assertTrue(readme.contains("not affiliated"))
        for (name in listOf("Cobblemon", "The Pokémon Company", "Create", "Applied Energistics 2")) {
            assertTrue(readme.contains(name), name)
        }
    }

    @Test
    fun `CD-REL-01 build workflow runs build and GameTests on main pushes and pull requests`() {
        val text = File(root, ".github/workflows/build.yml").readText()
        assertTrue(text.contains("pull_request:") && text.contains("push:") && text.contains("branches: [main]"))
        assertTrue(text.contains("./gradlew build") && text.contains("./gradlew runGameTestServer"))
    }

    @Test
    fun `CD-REL-02 security workflow runs CodeQL and dependency review`() {
        val text = File(root, ".github/workflows/security.yml").readText()
        assertTrue(text.contains("github/codeql-action/analyze"))
        assertTrue(text.contains("actions/dependency-review-action"))
    }

    @Test
    fun `CD-REL-03 dependabot covers gradle and github-actions`() {
        val text = File(root, ".github/dependabot.yml").readText()
        assertTrue(text.contains("package-ecosystem: gradle"))
        assertTrue(text.contains("package-ecosystem: github-actions"))
    }
}
