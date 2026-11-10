package codes.honor.cobblemondynamics.command

import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.stamina.RecoveryRule
import java.util.Locale
import kotlin.math.floor

/** The parts of a `/cobblemondynamics stamina` line, before the Pokémon's name and the lang keys turn them into text (CD-UI-15). */
object StaminaReport {
    /** `<name>: <percent>% Work Stamina, <state>, <rule>`. */
    const val LINE = "cobblemon_dynamics.command.stamina"
    const val NOT_POKEMON = "cobblemon_dynamics.command.stamina.not_pokemon"
    private const val STATE = "cobblemon_dynamics.state"
    private const val RULE = "cobblemon_dynamics.rule"

    data class Line(val percent: String, val stateKey: String, val ruleKey: String)

    /** [status] is null for a Pokémon that the engine does not run, which shows its saved value. */
    fun line(percent: Double, status: WorkerStatus?, rule: RecoveryRule): Line = Line(percentText(percent), stateKey(status), ruleKey(rule))

    /** Rounded down to one decimal, so a Tired Pokémon never reads 100.0. The tiny bias absorbs one-tick rounding error. */
    fun percentText(percent: Double): String = String.format(Locale.ROOT, "%.1f", floor(percent * 10 + 1e-6) / 10)

    fun stateKey(status: WorkerStatus?): String = "$STATE.${status?.name?.lowercase() ?: "none"}"

    fun ruleKey(rule: RecoveryRule): String = "$RULE.${rule.name.lowercase()}"
}
