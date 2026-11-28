package codes.honor.cobblemondynamics.client

import codes.honor.cobblemondynamics.engine.IdleReason
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.stamina.TraitFlags
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WorkerTooltipLinesTest {
    @Test
    fun `CD-SHF-07 CD-SHF-10 panel tooltip renders Slacking Off idle reason and active 5-bit trait badges`() {
        val slacking = WorkerTooltipLines.panelTooltip(
            percent = 73,
            status = WorkerStatus.SLACKING,
            idleReason = IdleReason.NONE,
            traitFlags = TraitFlags.SHINY or TraitFlags.ALPHA,
        )
        assertEquals(
            listOf(
                TextSpec.Key(StaminaBar.TOOLTIP, listOf(73)),
                TextSpec.Key("cobblemon_dynamics.state.slacking"),
                TextSpec.Key(
                    WorkerTooltipLines.TRAITS_LABEL,
                    listOf(
                        TextSpec.Key(
                            WorkerTooltipLines.TRAIT_JOIN,
                            listOf(TextSpec.Key(TraitBadge.SHINY.langKey), TextSpec.Key(TraitBadge.ALPHA.langKey)),
                        ),
                    ),
                ),
            ),
            slacking,
        )

        val idle = WorkerTooltipLines.panelTooltip(
            percent = 100,
            status = WorkerStatus.IDLE,
            idleReason = IdleReason.NEEDS_LEASH,
            traitFlags = 0,
        )
        assertEquals(
            listOf(
                TextSpec.Key(StaminaBar.TOOLTIP, listOf(100)),
                TextSpec.Key("cobblemon_dynamics.state.idle"),
                TextSpec.Key(WorkerTooltipLines.IDLE_LINE, listOf(TextSpec.Key("cobblemon_dynamics.idle.needs_leash"))),
            ),
            idle,
        )
    }

    @Test
    fun `CD-SHF-07 CD-SHF-10 head bar lines include slacking or idle reason and all five trait badges in order`() {
        val allTraits = TraitFlags.SHINY or TraitFlags.ALPHA or TraitFlags.MAJESTIC or TraitFlags.MASTERY or TraitFlags.MATE
        assertEquals(
            listOf(TraitBadge.SHINY, TraitBadge.ALPHA, TraitBadge.MAJESTIC, TraitBadge.MASTERY, TraitBadge.MATE),
            WorkerTooltipLines.activeBadges(allTraits),
        )
        val slackingHead = WorkerTooltipLines.headBarLines(WorkerStatus.SLACKING, IdleReason.NONE, TraitFlags.MATE)
        assertEquals(
            listOf(
                TextSpec.Key("cobblemon_dynamics.state.slacking"),
                TextSpec.Key(TraitBadge.MATE.langKey),
            ),
            slackingHead,
        )
        val workingClean = WorkerTooltipLines.headBarLines(WorkerStatus.WORKING, IdleReason.NONE, 0)
        assertEquals(emptyList<TextSpec>(), workingClean)
    }
}
