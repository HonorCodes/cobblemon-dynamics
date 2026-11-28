package codes.honor.cobblemondynamics.stamina

import codes.honor.cobblemondynamics.config.EngineSettings
import codes.honor.cobblemondynamics.handler.farming.AreaParams
import codes.honor.cobblemondynamics.handler.farming.CropArea
import codes.honor.cobblemondynamics.handler.water.FreezePace
import com.cobblemon.mod.common.api.pokemon.feature.FlagSpeciesFeature
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkTraitsTest {
    private val s = EngineSettings.DEFAULT

    @Test
    fun `CD-PRG-02 shiny worker gets 1_15 and SHINY flag, and non-shiny gets 1_0 without SHINY`() {
        val plain = WorkTraits.evaluate(shiny = false, settings = s)
        assertEquals(1.0, plain.combinedMultiplier, 1e-9)
        assertFalse(plain.isShiny)
        assertEquals(0, plain.flags)

        val shiny = WorkTraits.evaluate(shiny = true, settings = s)
        assertEquals(1.15, shiny.combinedMultiplier, 1e-9)
        assertTrue(shiny.isShiny)
        assertEquals(TraitFlags.SHINY, shiny.flags)
    }

    @Test
    fun `CD-PRG-03 alpha flag and aspects grant 1_25x multiplier, ALPHA flag, and +1 area count on all 4 area roles`() {
        assertTrue(WorkTraits.isAlpha(isAlphaFlag = true))
        assertTrue(WorkTraits.isAlpha(isAlphaFlag = false, aspects = setOf("alpha")))
        assertTrue(WorkTraits.isAlpha(isAlphaFlag = false, forcedAspects = setOf("Alpha")))
        assertFalse(WorkTraits.isAlpha(isAlphaFlag = false, aspects = setOf("shiny")))

        val alpha = WorkTraits.evaluate(alpha = true, settings = s)
        assertEquals(1.25, alpha.combinedMultiplier, 1e-9)
        assertTrue(alpha.isAlpha)
        assertEquals(TraitFlags.ALPHA, alpha.flags)

        val area = AreaParams.DEFAULT
        assertEquals(0, CropArea.count(1, area, isAlpha = false))
        assertEquals(1, CropArea.count(1, area, isAlpha = true))
        assertEquals(5, CropArea.count(50, area, isAlpha = false))
        assertEquals(6, CropArea.count(50, area, isAlpha = true))
        assertEquals(10, CropArea.count(100, area, isAlpha = false))
        assertEquals(11, CropArea.count(100, area, isAlpha = true))
        assertEquals(1, FreezePace.cellsPerCycle(isAlpha = false))
        assertEquals(2, FreezePace.cellsPerCycle(isAlpha = true))
    }

    @Test
    fun `CD-PRG-04 majestic detects aspects, forcedAspects, and enabled FlagSpeciesFeature while ignoring disabled`() {
        assertTrue(WorkTraits.isMajestic(aspects = setOf("majestic")))
        assertTrue(WorkTraits.isMajestic(forcedAspects = setOf("Majestic")))
        assertTrue(WorkTraits.isMajestic(features = listOf(FlagSpeciesFeature("majestic", true))))
        assertFalse(WorkTraits.isMajestic(features = listOf(FlagSpeciesFeature("majestic", false))))
        assertFalse(WorkTraits.isMajestic(features = listOf(FlagSpeciesFeature("other", true))))

        val maj = WorkTraits.evaluate(majestic = true, settings = s)
        assertEquals(1.35, maj.combinedMultiplier, 1e-9)
        assertTrue(maj.isMajestic)
        assertEquals(TraitFlags.MAJESTIC, maj.flags)
    }

    @Test
    fun `CD-PRG-05 Move Mastery matches damaging power 60+ matching-type moves or mastery_moves list`() {
        val thunderbolt = MoveFacts("thunderbolt", "electric", 90.0, isStatus = false)
        val ember = MoveFacts("ember", "fire", 40.0, isStatus = false)
        val growl = MoveFacts("growl", "normal", 0.0, isStatus = true)
        val flamethrower = MoveFacts("flamethrower", "fire", 90.0, isStatus = false)
        val rainDance = MoveFacts("raindance", "water", 0.0, isStatus = true)

        val withBolt = WorkTraits.evaluate(moves = listOf(thunderbolt), roleTypes = setOf("electric"), settings = s)
        assertEquals(1.20, withBolt.combinedMultiplier, 1e-9)
        assertTrue(withBolt.hasMastery)
        assertEquals(listOf("thunderbolt"), withBolt.masteryMoves)

        val listedStatus = WorkTraits.evaluate(
            moves = listOf(rainDance),
            roleTypes = setOf("water"),
            roleMasteryMoves = setOf("raindance"),
            settings = s,
        )
        assertEquals(1.20, listedStatus.combinedMultiplier, 1e-9)
        assertTrue(listedStatus.hasMastery)
        assertEquals(listOf("raindance"), listedStatus.masteryMoves)

        val lowPower = WorkTraits.evaluate(moves = listOf(ember), roleTypes = setOf("fire"), settings = s)
        assertEquals(1.0, lowPower.combinedMultiplier, 1e-9)
        assertFalse(lowPower.hasMastery)

        val statusUnlisted = WorkTraits.evaluate(moves = listOf(growl), roleTypes = setOf("normal"), settings = s)
        assertEquals(1.0, statusUnlisted.combinedMultiplier, 1e-9)
        assertFalse(statusUnlisted.hasMastery)

        val offType = WorkTraits.evaluate(moves = listOf(flamethrower), roleTypes = setOf("electric"), settings = s)
        assertEquals(1.0, offType.combinedMultiplier, 1e-9)
        assertFalse(offType.hasMastery)
    }
}
