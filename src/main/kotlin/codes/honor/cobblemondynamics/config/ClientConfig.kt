package codes.honor.cobblemondynamics.config

import net.neoforged.neoforge.common.ModConfigSpec

/** The client config, `config/cobblemon_dynamics-client.toml`. */
object ClientConfig {
    private val workSoundsValue: ModConfigSpec.BooleanValue
    private val headBarRangeValue: ModConfigSpec.IntValue
    private val panelBarsValue: ModConfigSpec.BooleanValue

    val SPEC: ModConfigSpec

    init {
        val b = ModConfigSpec.Builder()

        b.push("effects")
        workSoundsValue = b.comment("Play the sounds of the work effects.").define("workSounds", false)
        b.pop()

        b.push("hud")
        headBarRangeValue = b.comment("Blocks within which workers show their head bars.")
            .defineInRange("headBarRange", 16, ConfigRanges.HEAD_BAR_RANGE.first, ConfigRanges.HEAD_BAR_RANGE.last)
        panelBarsValue = b.comment("Show the worker bars in the pasture panel.").define("panelBars", true)
        b.pop()

        SPEC = b.build()
    }

    /** The default that the spec writes to a new client config. */
    val defaultWorkSounds: Boolean get() = workSoundsValue.default

    val workSounds: Boolean get() = workSoundsValue.get()
    val headBarRange: Int get() = headBarRangeValue.get()
    val panelBars: Boolean get() = panelBarsValue.get()

    fun hasKey(path: String): Boolean = SPEC.spec.contains(path)
}
