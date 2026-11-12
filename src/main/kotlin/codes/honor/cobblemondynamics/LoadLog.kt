package codes.honor.cobblemondynamics

import java.util.concurrent.ConcurrentHashMap

/**
 * One INFO line per distinct text between two calls of [newLoad]. A rebuild after a config change repeats the lines of the last role
 * file load, so each line logs once per load, and a changed line logs again (CD-CFG-10, CD-CMP-10).
 */
class LoadLog(private val sink: (String) -> Unit) {
    private val logged: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun newLoad() = logged.clear()

    fun info(line: String) {
        if (logged.add(line)) sink(line)
    }
}
