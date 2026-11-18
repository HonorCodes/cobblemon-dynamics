package codes.honor.cobblemondynamics.build

import java.io.File

/** The table of the mods that the roles support, in ADR-0016, at the versions that the 1.2 plan pins. */
object SupportedMods {
    const val ADR = "docs/adr/0016-zero-setup-optional-mod-compatibility.md"

    /** A row of the table: the mod's name without the libraries after ", with", its mod id and its version. */
    data class Mod(val name: String, val id: String, val version: String)

    /** Each row of the table whose header starts with `| Mod | Mod id | Version |`. A row of another form fails the read. */
    fun read(root: File): List<Mod> {
        val lines = File(root, ADR).readLines()
        val header = lines.indexOfFirst { it.startsWith("| Mod | Mod id | Version |") }
        require(header >= 0) { "$ADR has no table of mods" }
        val rows = lines.drop(header + 2).takeWhile { it.startsWith("|") }
        require(rows.isNotEmpty()) { "the table of mods of $ADR has no row" }
        return rows.map { line ->
            val cells = line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }
            require(cells.size == 6 && cells[1].startsWith("`")) { "a row of the table of mods of $ADR has another form: $line" }
            Mod(
                name = cells[0].substringBefore(", with "),
                id = cells[1].substringAfter('`').substringBefore('`'),
                version = cells[2].substringBefore(" (").trim(),
            )
        }
    }
}
