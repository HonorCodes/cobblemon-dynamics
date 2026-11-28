package codes.honor.cobblemondynamics.client

import codes.honor.cobblemondynamics.command.StaminaReport
import codes.honor.cobblemondynamics.engine.IdleReason
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.stamina.TraitFlags
import net.minecraft.network.chat.Component

sealed interface TextSpec {
    data class Key(val key: String, val args: List<Any> = emptyList()) : TextSpec

    fun toComponent(): Component = when (this) {
        is Key -> {
            val resolved = args.map { arg ->
                if (arg is TextSpec) arg.toComponent() else arg
            }.toTypedArray()
            Component.translatable(key, *resolved)
        }
    }
}

enum class TraitBadge(val flag: Int, val langKey: String) {
    SHINY(TraitFlags.SHINY, "cobblemon_dynamics.trait.shiny"),
    ALPHA(TraitFlags.ALPHA, "cobblemon_dynamics.trait.alpha"),
    MAJESTIC(TraitFlags.MAJESTIC, "cobblemon_dynamics.trait.majestic"),
    MASTERY(TraitFlags.MASTERY, "cobblemon_dynamics.trait.mastery"),
    MATE(TraitFlags.MATE, "cobblemon_dynamics.trait.mate"),
}

object RoleNames {
    const val BREW_STOKER = "cobblemon_dynamics.role.brew_stoker"
    const val BREWER = "cobblemon_dynamics.role.brewer"
    const val CRANKER = "cobblemon_dynamics.role.cranker"
    const val CRYSTAL_TENDER = "cobblemon_dynamics.role.crystal_tender"
    const val CULTIVATOR = "cobblemon_dynamics.role.cultivator"
    const val FAN_CATALYST = "cobblemon_dynamics.role.fan_catalyst"
    const val FREEZER = "cobblemon_dynamics.role.freezer"
    const val GENERATOR = "cobblemon_dynamics.role.generator"
    const val HARVESTER = "cobblemon_dynamics.role.harvester"
    const val HEALER = "cobblemon_dynamics.role.healer"
    const val HEATER = "cobblemon_dynamics.role.heater"
    const val LUMBERJACK = "cobblemon_dynamics.role.lumberjack"
    const val MAGNET = "cobblemon_dynamics.role.magnet"
    const val MUSE = "cobblemon_dynamics.role.muse"
    const val PICKER = "cobblemon_dynamics.role.picker"
    const val PUMP = "cobblemon_dynamics.role.pump"
    const val SENTRY = "cobblemon_dynamics.role.sentry"
    const val SOUL_COLLECTOR = "cobblemon_dynamics.role.soul_collector"
    const val STOKER = "cobblemon_dynamics.role.stoker"
    const val SUPERHEATER = "cobblemon_dynamics.role.superheater"
    const val TAILWIND = "cobblemon_dynamics.role.tailwind"
    const val TELEKINESIS = "cobblemon_dynamics.role.telekinesis"
    const val WHEEL_DRIVER = "cobblemon_dynamics.role.wheel_driver"
    const val CUSTOM = "cobblemon_dynamics.role.custom"

    fun specFor(rolePath: String): TextSpec {
        val normalized = rolePath.substringAfter(':').lowercase()
        val key = when {
            normalized == "brew_stoker" -> BREW_STOKER
            normalized == "brewer" || normalized.startsWith("brewer_") -> BREWER
            normalized == "cranker" || normalized.startsWith("cranker_") -> CRANKER
            normalized == "crystal_tender" || normalized.startsWith("crystal_tender_") -> CRYSTAL_TENDER
            normalized == "cultivator" || normalized.startsWith("cultivator_") -> CULTIVATOR
            normalized == "fan_catalyst" || normalized.startsWith("fan_catalyst_") -> FAN_CATALYST
            normalized == "freezer" || normalized.startsWith("freezer_") -> FREEZER
            normalized == "generator" || normalized.startsWith("generator_") -> GENERATOR
            normalized == "harvester" || normalized.startsWith("harvester_") -> HARVESTER
            normalized == "healer" -> HEALER
            normalized == "heater" || normalized.startsWith("heater_") -> HEATER
            normalized == "lumberjack" || normalized.startsWith("lumberjack_") -> LUMBERJACK
            normalized == "magnet" || normalized.startsWith("magnet_") -> MAGNET
            normalized == "muse" || normalized.startsWith("muse_") -> MUSE
            normalized == "picker" || normalized.startsWith("picker_") -> PICKER
            normalized == "pump" || normalized.startsWith("pump_") -> PUMP
            normalized == "sentry" || normalized.startsWith("sentry_") -> SENTRY
            normalized == "soul_collector" || normalized.startsWith("soul_collector_") -> SOUL_COLLECTOR
            normalized == "stoker" || normalized.startsWith("stoker_") -> STOKER
            normalized == "superheater" || normalized.startsWith("superheater_") -> SUPERHEATER
            normalized == "tailwind" || normalized.startsWith("tailwind_") -> TAILWIND
            normalized == "telekinesis" || normalized.startsWith("telekinesis_") -> TELEKINESIS
            normalized == "wheel_driver" || normalized.startsWith("wheel_driver_") -> WHEEL_DRIVER
            else -> return TextSpec.Key(CUSTOM, listOf(normalized))
        }
        return TextSpec.Key(key)
    }
}

object WorkerTooltipLines {
    const val IDLE_LINE = "cobblemon_dynamics.ui.idle_reason"
    const val TRAITS_LABEL = "cobblemon_dynamics.trait.label"
    const val TRAIT_JOIN = "cobblemon_dynamics.trait.join"

    fun activeBadges(traitFlags: Int): List<TraitBadge> = TraitBadge.entries.filter { TraitFlags.has(traitFlags, it.flag) }

    fun joinSpecs(specs: List<TextSpec>): TextSpec? = when {
        specs.isEmpty() -> null
        specs.size == 1 -> specs.first()
        else -> specs.reduce { left, right -> TextSpec.Key(TRAIT_JOIN, listOf(left, right)) }
    }

    fun compactTraitLine(traitFlags: Int): TextSpec? = joinSpecs(activeBadges(traitFlags).map { TextSpec.Key(it.langKey) })

    fun traitLine(traitFlags: Int): TextSpec? = compactTraitLine(traitFlags)?.let { TextSpec.Key(TRAITS_LABEL, listOf(it)) }

    fun panelTooltip(percent: Int, status: WorkerStatus, idleReason: IdleReason = IdleReason.NONE, traitFlags: Int = 0): List<TextSpec> {
        val lines = ArrayList<TextSpec>(4)
        lines += TextSpec.Key(StaminaBar.TOOLTIP, listOf(percent))
        lines += TextSpec.Key(StaminaReport.stateKey(status))
        val reasonKey = idleReason.takeIf { it != IdleReason.NONE }?.langKey
        if (reasonKey != null) {
            lines += TextSpec.Key(IDLE_LINE, listOf(TextSpec.Key(reasonKey)))
        }
        traitLine(traitFlags)?.let(lines::add)
        return lines
    }

    fun headBarLines(status: WorkerStatus, idleReason: IdleReason, traitFlags: Int): List<TextSpec> {
        val lines = ArrayList<TextSpec>(2)
        if (status == WorkerStatus.SLACKING) {
            lines += TextSpec.Key(StaminaReport.stateKey(WorkerStatus.SLACKING))
        } else {
            val reasonKey = idleReason.takeIf { it != IdleReason.NONE }?.langKey
            if (reasonKey != null) lines += TextSpec.Key(reasonKey)
        }
        compactTraitLine(traitFlags)?.let(lines::add)
        return lines
    }
}
