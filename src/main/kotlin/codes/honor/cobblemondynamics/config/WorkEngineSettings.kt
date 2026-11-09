package codes.honor.cobblemondynamics.config

/** The live engine settings. The config listeners replace the snapshot when the server config loads or changes. */
object WorkEngineSettings {
    @Volatile
    var current: EngineSettings = EngineSettings.DEFAULT

    fun reload() {
        current = ServerConfig.snapshot()
    }
}
