package codes.honor.cobblemondynamics.config

import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.common.ModConfigSpec

/** The server config, `config/cobblemon_dynamics-server.toml`. Engine code reads [WorkEngineSettings.current]. */
object ServerConfig {
    private const val BED_TIERS = 4

    private val horizontalRadius: ModConfigSpec.IntValue
    private val verticalRadius: ModConfigSpec.IntValue
    private val scanIntervalTicks: ModConfigSpec.IntValue
    private val maxBlockChecksPerTick: ModConfigSpec.IntValue
    private val travelTimeoutTicks: ModConfigSpec.IntValue
    private val retryWaitTicks: ModConfigSpec.IntValue
    private val standbyMoveTicks: ModConfigSpec.IntValue
    private val effectBroadcastRadius: ModConfigSpec.IntValue
    private val toggleCooldownTicks: ModConfigSpec.IntValue
    private val pendingSelectionTicks: ModConfigSpec.IntValue
    private val ticksPerLevel: ModConfigSpec.IntValue
    private val healerMinutesAtLevel1: ModConfigSpec.IntValue
    private val healerMinutesAtLevel100: ModConfigSpec.IntValue
    private val flushIntervalTicks: ModConfigSpec.IntValue
    private val bedFactors: ModConfigSpec.ConfigValue<List<Int>>
    private val healCost: ModConfigSpec.DoubleValue
    private val lowStaminaThreshold: ModConfigSpec.DoubleValue
    private val disabledRoles: ModConfigSpec.ConfigValue<List<String>>
    private val compatCobbleworkers: ModConfigSpec.BooleanValue
    private val compatCobblebase: ModConfigSpec.BooleanValue
    private val quickWorkEnabled: ModConfigSpec.BooleanValue
    private val quickWorkTargetRange: ModConfigSpec.IntValue
    private val quickWorkLeashRange: ModConfigSpec.IntValue
    private val maxWorkersPerContainer: ModConfigSpec.IntValue

    val SPEC: ModConfigSpec

    init {
        val d = EngineSettings.DEFAULT
        val b = ModConfigSpec.Builder()

        b.comment("The box around each pasture block in which its workers look for targets.").push("workArea")
        horizontalRadius = b.int(
            "horizontalRadius",
            d.horizontalRadius,
            ConfigRanges.WORK_RADIUS,
            "Horizontal work radius in blocks. Never above Cobblemon's pastureMaxWanderDistance.",
        )
        verticalRadius = b.int(
            "verticalRadius",
            d.verticalRadius,
            ConfigRanges.WORK_RADIUS,
            "Vertical work radius in blocks. Never above Cobblemon's pastureMaxWanderDistance.",
        )
        b.pop()

        b.comment("Scan, travel and Standby timing of the work engine.").push("engine")
        scanIntervalTicks = b.int(
            "scanIntervalTicks",
            d.scanIntervalTicks,
            ConfigRanges.SCAN_INTERVAL,
            "Ticks between the starts of two scan passes of one pasture.",
        )
        maxBlockChecksPerTick = b.int(
            "maxBlockChecksPerTick",
            d.maxBlockChecksPerTick,
            ConfigRanges.BLOCK_CHECKS,
            "Most block positions that the scan of one pasture checks in one tick.",
        )
        travelTimeoutTicks = b.int(
            "travelTimeoutTicks",
            d.travelTimeoutTicks,
            ConfigRanges.TRAVEL_TIMEOUT,
            "Ticks that a worker has to reach its target before the engine releases or holds the claim.",
        )
        retryWaitTicks = b.int(
            "retryWaitTicks",
            d.retryWaitTicks,
            ConfigRanges.RETRY_WAIT,
            "Ticks that the engine skips a target after a missed arrival, and that a leashed worker waits before it travels again.",
        )
        standbyMoveTicks = b.int(
            "standbyMoveTicks",
            d.standbyMoveTicks,
            ConfigRanges.STANDBY_MOVE,
            "Ticks that an auto-assigned worker stays in Standby before it can move to a target with demand.",
        )
        effectBroadcastRadius = b.int(
            "effectBroadcastRadius",
            d.effectBroadcastRadius,
            ConfigRanges.EFFECT_RADIUS,
            "Blocks around a worker in which players receive its work effects.",
        )
        b.pop()

        b.push("workMode")
        toggleCooldownTicks = b.int(
            "toggleCooldownTicks",
            d.toggleCooldownTicks,
            ConfigRanges.TOGGLE_COOLDOWN,
            "Ticks after a player's accepted Work Mode request during which that player's next request is ignored.",
        )
        b.pop()

        b.push("leash")
        pendingSelectionTicks = b.int(
            "pendingSelectionTicks",
            d.pendingSelectionTicks,
            ConfigRanges.PENDING_SELECTION,
            "Ticks that a Work Leash keeps a pending selection before it clears it.",
        )
        b.pop()

        b.comment("The logistics roles, the Magnet and Telekinesis, share their containers.").push("logistics")
        maxWorkersPerContainer = b.int(
            "maxWorkersPerContainer",
            d.maxWorkersPerContainer,
            ConfigRanges.WORKERS_PER_CONTAINER,
            "Most workers of logistics roles on one container at once: as a Magnet's container, a Telekinesis source or destination.",
        )
        b.pop()

        b.comment("Quick Work: a party Pokémon that its owner sends out at a block works that block.").push("quickWork")
        quickWorkEnabled = b.comment("Start Quick Work orders. With false, a send-out never starts one.")
            .define("enabled", d.quickWorkEnabled)
        quickWorkTargetRange = b.int(
            "targetRange",
            d.quickWorkTargetRange,
            ConfigRanges.QUICK_WORK_RANGE,
            "Blocks that the view of a player who sends out a Pokémon reaches to find its target. Never more than leashRange.",
        )
        quickWorkLeashRange = b.int(
            "leashRange",
            d.quickWorkLeashRange,
            ConfigRanges.QUICK_WORK_RANGE,
            "Blocks from the owner's eyes to the target at which a Quick Work order ends.",
        )
        b.pop()

        b.push("stamina")
        ticksPerLevel = b.int(
            "ticksPerLevel",
            d.ticksPerLevel,
            ConfigRanges.TICKS_PER_LEVEL,
            "Ticks of work capacity per level of the Pokémon.",
        )
        healerMinutesAtLevel1 = b.int(
            "healerRecoveryMinutesAtLevel1",
            d.healerMinutesAtLevel1,
            ConfigRanges.HEALER_MINUTES,
            "Minutes of healer recovery at level 1.",
        )
        healerMinutesAtLevel100 = b.int(
            "healerRecoveryMinutesAtLevel100",
            d.healerMinutesAtLevel100,
            ConfigRanges.HEALER_MINUTES,
            "Minutes of healer recovery at level 100.",
        )
        flushIntervalTicks = b.int(
            "flushIntervalTicks",
            d.flushIntervalTicks,
            ConfigRanges.FLUSH_INTERVAL,
            "Ticks between two writes of a worker's Work Stamina to its saved data.",
        )
        b.pop()

        b.push("beds")
        bedFactors = b.comment("Recovery speed factor of each of the four bed tiers, lowest tier first.")
            .defineList(
                listOf("speedFactors"),
                { d.bedFactors },
                { ConfigRanges.BED_FACTOR.first },
                { it is Int && it in ConfigRanges.BED_FACTOR },
                ModConfigSpec.Range.of(BED_TIERS, BED_TIERS),
            )
        b.pop()

        b.push("heal")
        healCost = b.comment("Share of Work Stamina that one heal costs the healer.")
            .defineInRange("cost", d.healCost, ConfigRanges.HEAL_COST.start, ConfigRanges.HEAL_COST.endInclusive)
        lowStaminaThreshold = b.comment("Share of Work Stamina under which an ally needs a heal.")
            .defineInRange(
                "lowStaminaThreshold",
                d.lowStaminaThreshold,
                ConfigRanges.LOW_STAMINA_THRESHOLD.start,
                ConfigRanges.LOW_STAMINA_THRESHOLD.endInclusive,
            )
        b.pop()

        b.push("roles")
        disabledRoles = b.comment("Role ids to disable, for example \"cobblemon_dynamics:stoker\".")
            .defineListAllowEmpty(
                "disabled",
                emptyList(),
                { "cobblemon_dynamics:stoker" },
                { it is String && ResourceLocation.tryParse(it) != null },
            )
        b.pop()

        b.comment("Compatibility with optional neighbor mods.").push("compat")
        compatCobbleworkers = b.comment("When Cobbleworkers is present, mark Work Mode workers as resting for it.")
            .define("cobbleworkers", d.compatCobbleworkers)
        compatCobblebase = b.comment("When Cobblebase is present, stop its pasture tick for pastures in Work Mode.")
            .define("cobblebase", d.compatCobblebase)
        b.pop()

        SPEC = b.build()
    }

    /** Reads every value. Call it only while the server config is loaded. */
    fun snapshot(): EngineSettings = EngineSettings(
        horizontalRadius = horizontalRadius.get(),
        verticalRadius = verticalRadius.get(),
        scanIntervalTicks = scanIntervalTicks.get(),
        maxBlockChecksPerTick = maxBlockChecksPerTick.get(),
        travelTimeoutTicks = travelTimeoutTicks.get(),
        retryWaitTicks = retryWaitTicks.get(),
        standbyMoveTicks = standbyMoveTicks.get(),
        effectBroadcastRadius = effectBroadcastRadius.get(),
        toggleCooldownTicks = toggleCooldownTicks.get(),
        pendingSelectionTicks = pendingSelectionTicks.get(),
        ticksPerLevel = ticksPerLevel.get(),
        healerMinutesAtLevel1 = healerMinutesAtLevel1.get(),
        healerMinutesAtLevel100 = healerMinutesAtLevel100.get(),
        flushIntervalTicks = flushIntervalTicks.get(),
        bedFactors = readBedFactors(),
        healCost = healCost.get(),
        lowStaminaThreshold = lowStaminaThreshold.get(),
        disabledRoles = disabledRoles.get().mapNotNull { ResourceLocation.tryParse(it) }.toSet(),
        compatCobbleworkers = compatCobbleworkers.get(),
        compatCobblebase = compatCobblebase.get(),
        quickWorkEnabled = quickWorkEnabled.get(),
        quickWorkTargetRange = quickWorkTargetRange.get(),
        quickWorkLeashRange = quickWorkLeashRange.get(),
        maxWorkersPerContainer = maxWorkersPerContainer.get(),
    )

    fun hasKey(path: String): Boolean = SPEC.spec.contains(path)

    fun isValid(path: String, value: Any): Boolean = (SPEC.spec.get<Any>(path) as ModConfigSpec.ValueSpec).test(value)

    /** NeoForge keeps a list of the wrong size and drops bad elements, so the tier count is checked here. */
    private fun readBedFactors(): List<Int> {
        val factors = bedFactors.get().filter { it in ConfigRanges.BED_FACTOR }
        return if (factors.size == BED_TIERS) factors else EngineSettings.DEFAULT.bedFactors
    }

    private fun ModConfigSpec.Builder.int(name: String, default: Int, range: IntRange, comment: String): ModConfigSpec.IntValue =
        comment(comment).defineInRange(name, default, range.first, range.last)
}
