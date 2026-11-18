package codes.honor.cobblemondynamics.build

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** The compile-only dependencies of the mods of ADR-0016 in `build.gradle`, with their pins in `gradle.properties`. */
class OptionalModPinsTest {
    private val root = File(System.getProperty("cd.projectDir"))

    @Test
    fun `CD-OPT-14 each compile-only mod of ADR-0016 is pinned at its version, not transitive, and named on the integration page`() {
        val mods = SupportedMods.read(root).associateBy { it.id }
        val properties = File(root, "gradle.properties").readLines()
        val page = File(root, "docs/modpack-integration.md").readText()
        val lines = COMPILE_ONLY.findAll(File(root, "build.gradle").readText()).toList()
        val coordinates = lines.map { it.groupValues[1] }.toSet()
        val problems = mutableListOf<String>()
        problems += (MODS.keys - coordinates).map { "build.gradle has no compileOnly of $it" }
        problems += (coordinates - MODS.keys - OTHERS).map { "build.gradle has a compileOnly of $it, which this test does not know" }
        for (line in lines) {
            val coordinate = line.groupValues[1]
            val id = MODS[coordinate] ?: continue
            val mod = mods[id]
            if (mod == null) {
                problems += "ADR-0016 has no mod $id"
                continue
            }
            if ("transitive = false" !in line.groupValues[3]) problems += "$coordinate keeps its transitive dependencies"
            val pin = pin(properties, line.groupValues[2])
            if (pin == null || !pinned(pin, mod)) problems += "$coordinate is at ${pin?.value}, not ${mod.name} ${mod.version}"
            if (mod.version !in page) problems += "docs/modpack-integration.md does not name ${mod.name} ${mod.version}"
        }
        assertTrue(problems.isEmpty(), "CD-OPT-14: $problems")
    }

    private class Pin(val value: String, val comment: String)

    /** The value of the property [name] in [lines], with the comment lines right above it. */
    private fun pin(lines: List<String>, name: String): Pin? {
        val index = lines.indexOfFirst { it.startsWith("$name=") }
        if (index < 0) return null
        return Pin(lines[index].substringAfter('=').trim(), lines.subList(0, index).takeLastWhile { it.startsWith("#") }.joinToString(" "))
    }

    /**
     * True when the Maven version is the mod's version, or holds it between `-` and `+`, as `1.21.1-3.16.3` holds 3.16.3. A Modrinth
     * version id pins a file whose version string a file of another loader shares, and the comment above it names the mod's version.
     */
    private fun pinned(pin: Pin, mod: SupportedMods.Mod): Boolean = pin.value == mod.version ||
        mod.version in pin.value.split('-', '+') ||
        (VERSION_ID.matches(pin.value) && "${mod.name} ${mod.version}" in pin.comment)

    private companion object {
        val COMPILE_ONLY = Regex("""^\s*compileOnly\("([\w.-]+:[\w.-]+):\$\{(\w+)}(?::\w+)?"\)(.*)$""", RegexOption.MULTILINE)
        val VERSION_ID = Regex("""[A-Za-z0-9]{8}""")

        /** The Maven coordinates of the mods of ADR-0016 that the code names, with the mod id of each in the table. */
        val MODS = mapOf(
            "dev.shadowsoffire:ApothicEnchanting" to "apothic_enchanting",
            "org.appliedenergistics:appliedenergistics2" to "ae2",
            "maven.modrinth:immersiveengineering" to "immersiveengineering",
            "maven.modrinth:mekanism" to "mekanism",
            "maven.modrinth:mekanism-generators" to "mekanismgenerators",
            "maven.modrinth:railcraft-reborn" to "railcraft",
            "maven.modrinth:pneumaticcraft-repressurized" to "pneumaticcraft",
            "maven.modrinth:botany-pots" to "botanypots",
            "maven.modrinth:iron-furnaces" to "ironfurnaces",
            "maven.modrinth:cooking-for-blockheads" to "cookingforblockheads",
            "maven.modrinth:forbidden-arcanus" to "forbidden_arcanus",
            "maven.modrinth:cobblemon-incubator" to "cobblemon_incubator",
            "maven.modrinth:ars-nouveau" to "ars_nouveau",
            "maven.modrinth:createaddition" to "createaddition",
            "maven.modrinth:create-enchantment-industry" to "create_enchantment_industry",
            "maven.modrinth:irons-spells-n-spellbooks" to "irons_spellbooks",
            "maven.modrinth:easy-magic" to "easymagic",
            "maven.modrinth:enchanting-infuser" to "enchantinginfuser",
            "maven.modrinth:experiencelib" to "experiencelib",
            "maven.modrinth:dynamictrees" to "dynamictrees",
        )

        /** The other compile-only dependencies: Create, the claim mods, and the libraries of the mods above. */
        val OTHERS = setOf(
            "com.simibubi.create:create-1.21.1",
            "dev.ftb.mods:ftb-chunks-neoforge",
            "dev.ftb.mods:ftb-teams-neoforge",
            "dev.ftb.mods:ftb-library-neoforge",
            "maven.modrinth:open-parties-and-claims",
            "com.ldtteam:minecolonies",
            "com.ldtteam:structurize",
            "com.ldtteam:blockui",
            "maven.modrinth:bookshelf-lib",
            "maven.modrinth:balm",
            "maven.modrinth:valhelsia-core",
            "maven.modrinth:geckolib",
        )
    }
}
