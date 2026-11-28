package codes.honor.cobblemondynamics.compat.jade

import codes.honor.cobblemondynamics.client.PastureShelfState
import codes.honor.cobblemondynamics.client.RoleNames
import codes.honor.cobblemondynamics.client.StaminaBar
import codes.honor.cobblemondynamics.client.TextSpec
import codes.honor.cobblemondynamics.client.TraitBadge
import codes.honor.cobblemondynamics.client.WorkerTooltipLines
import codes.honor.cobblemondynamics.engine.IdleReason
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.stamina.TraitFlags
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JadeTooltipRulesTest {
    @Test
    fun `CD-SHF-08 worker lines show role state stamina idle reason traits and multiplier`() {
        val working = JadeTooltipRules.workerLines(
            percent = 84,
            status = WorkerStatus.SLACKING,
            idleReason = IdleReason.NONE,
            traitFlags = TraitFlags.SHINY or TraitFlags.MASTERY,
            rolePath = "stoker_ironfurnaces",
            multiplier = 1.38,
        )
        assertEquals(
            listOf(
                TextSpec.Key(
                    JadeTooltipRules.WORKER_ROLE_STATE,
                    listOf(TextSpec.Key(RoleNames.STOKER), TextSpec.Key("cobblemon_dynamics.state.slacking")),
                ),
                TextSpec.Key(StaminaBar.TOOLTIP, listOf(84)),
                TextSpec.Key(
                    WorkerTooltipLines.TRAITS_LABEL,
                    listOf(
                        TextSpec.Key(
                            WorkerTooltipLines.TRAIT_JOIN,
                            listOf(TextSpec.Key(TraitBadge.SHINY.langKey), TextSpec.Key(TraitBadge.MASTERY.langKey)),
                        ),
                    ),
                ),
                TextSpec.Key(JadeTooltipRules.WORKER_MULTIPLIER, listOf("1.38")),
            ),
            working,
        )

        val idle = JadeTooltipRules.workerLines(
            percent = 100,
            status = WorkerStatus.IDLE,
            idleReason = IdleReason.LEVEL_TOO_LOW,
            traitFlags = 0,
            rolePath = null,
            multiplier = 1.0,
        )
        assertEquals(
            listOf(
                TextSpec.Key(JadeTooltipRules.WORKER_STATE, listOf(TextSpec.Key("cobblemon_dynamics.state.idle"))),
                TextSpec.Key(StaminaBar.TOOLTIP, listOf(100)),
                TextSpec.Key(WorkerTooltipLines.IDLE_LINE, listOf(TextSpec.Key("cobblemon_dynamics.idle.level_too_low"))),
                TextSpec.Key(JadeTooltipRules.WORKER_MULTIPLIER, listOf("1")),
            ),
            idle,
        )
        assertEquals("1.5", JadeTooltipRules.formatMultiplier(1.5))
    }

    @Test
    fun `CD-SHF-09 machine pasture and bed lines format worker role lock and occupant`() {
        val machine = JadeTooltipRules.machineLines(
            listOf(WorkedMachineEntry("Charizard", "superheater_createaddition", WorkerStatus.WORKING)),
        )
        assertEquals(
            listOf(
                TextSpec.Key(
                    JadeTooltipRules.MACHINE_WORKER,
                    listOf(
                        "Charizard",
                        TextSpec.Key(RoleNames.SUPERHEATER),
                        TextSpec.Key("cobblemon_dynamics.state.working"),
                    ),
                ),
            ),
            machine,
        )

        val pasture = JadeTooltipRules.pastureLines(workMode = true, radius = 12, locked = true, workerCount = 3)
        assertEquals(
            listOf(
                TextSpec.Key(JadeTooltipRules.PASTURE_ACTIVE, listOf(3, 12)),
                TextSpec.Key(PastureShelfState.ACCESS_LOCKED),
            ),
            pasture,
        )

        assertEquals(
            listOf(
                TextSpec.Key(JadeTooltipRules.BED_FACTOR, listOf(8)),
                TextSpec.Key(JadeTooltipRules.BED_OCCUPIED, listOf("Snorlax")),
            ),
            JadeTooltipRules.bedLines(factor = 8, sleeperName = "Snorlax"),
        )
        assertEquals(
            listOf(
                TextSpec.Key(JadeTooltipRules.BED_FACTOR, listOf(2)),
                TextSpec.Key(JadeTooltipRules.BED_FREE),
            ),
            JadeTooltipRules.bedLines(factor = 2, sleeperName = null),
        )
    }
}
