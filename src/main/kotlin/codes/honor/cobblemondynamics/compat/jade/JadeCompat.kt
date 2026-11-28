package codes.honor.cobblemondynamics.compat.jade

import codes.honor.cobblemondynamics.compat.CompatLines

object JadeCompat {
    const val ADAPTER = "jade_plugin"

    fun register() {
        CompatLines.register("jade", ADAPTER)
    }
}
