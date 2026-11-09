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
}
