package codes.honor.cobblemondynamics.config

import net.neoforged.neoforge.common.ModConfigSpec

/** The client config, `config/cobblemon_dynamics-client.toml`. It has no work sound option, because work is silent (CD-CFG-06). */
object ClientConfig {
    private val headBarRangeValue: ModConfigSpec.IntValue
    private val panelBarsValue: ModConfigSpec.BooleanValue

    val SPEC: ModConfigSpec

    init {
        val b = ModConfigSpec.Builder()

        b.push("hud")
        headBarRangeValue = b.comment("Blocks within which workers show their head bars.")
            .defineInRange("headBarRange", 16, ConfigRanges.HEAD_BAR_RANGE.first, ConfigRanges.HEAD_BAR_RANGE.last)
        panelBarsValue = b.comment("Show the worker bars in the pasture panel.").define("panelBars", true)
        b.pop()

        SPEC = b.build()
    }

    val headBarRange: Int get() = headBarRangeValue.get()
    val panelBars: Boolean get() = panelBarsValue.get()

    fun hasKey(path: String): Boolean = SPEC.spec.contains(path)
}
