package codes.honor.cobblemondynamics.handler.farming

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The facts of each family come from the adapter table of ADR-0015. */
class CropRulesTest {
    private val wheat = CropFacts(cropBlock = true, bonemealable = true, age = 7, ageMax = 7, cropMaxAge = true)
    private val youngWheat = wheat.copy(age = 6, cropMaxAge = false)
    private val bush = CropFacts(bonemealable = true, age = 3, ageMax = 3, adapterTags = setOf(HarvestKind.PICK_TO_AGE_1))
    private val grains = CropFacts(cropBlock = true, bonemealable = true, age = 6, ageMax = 6, cropMaxAge = true, upperPresent = true)
    private val hemp = CropFacts(cropBlock = true, bonemealable = true, age = 4, ageMax = 4, cropMaxAge = true, upperPresent = true)
    private val tree = CropFacts(berryTree = true, bonemealable = true, age = 5, ageMax = 5)
    private val vines = CropFacts(caveVines = true, bonemealable = true, berries = true)

    @Test
    fun `CD-HRV-04 each family is fully grown at its stage and not one stage lower`() {
        assertTrue(CropRules.isFullyGrown(HarvestKind.CROP, wheat))
        assertFalse(CropRules.isFullyGrown(HarvestKind.CROP, youngWheat))
        assertTrue(CropRules.isFullyGrown(HarvestKind.PICK_TO_AGE_1, bush))
        assertFalse(CropRules.isFullyGrown(HarvestKind.PICK_TO_AGE_1, bush.copy(age = 2)))
        assertTrue(CropRules.isFullyGrown(HarvestKind.UPPER_HALF, grains))
        assertFalse(CropRules.isFullyGrown(HarvestKind.UPPER_HALF, grains.copy(age = 5, cropMaxAge = false)))
        assertFalse(CropRules.isFullyGrown(HarvestKind.UPPER_HALF, grains.copy(upperPresent = false)), "a lower half with no top")
        assertTrue(CropRules.isFullyGrown(HarvestKind.UPPER_HALF, hemp))
        assertFalse(CropRules.isFullyGrown(HarvestKind.UPPER_HALF, hemp.copy(upperPresent = false)))
        assertTrue(CropRules.isFullyGrown(HarvestKind.BERRY_TREE, tree))
        assertFalse(CropRules.isFullyGrown(HarvestKind.BERRY_TREE, tree.copy(age = 4)))
        assertTrue(CropRules.isFullyGrown(HarvestKind.CAVE_VINES, vines))
        assertFalse(CropRules.isFullyGrown(HarvestKind.CAVE_VINES, vines.copy(berries = false)))
    }

    @Test
    fun `CD-HRV-04 a block with no age is grown at once, and harvest-never never is`() {
        // The Aether's grown berry bush has no age property, and cocoa is grown at age 2.
        assertTrue(CropRules.isFullyGrown(HarvestKind.BREAK_AND_REPLANT, CropFacts()))
        assertTrue(CropRules.isFullyGrown(HarvestKind.BREAK_AND_REPLANT, CropFacts(age = 2, ageMax = 2)))
        assertFalse(CropRules.isFullyGrown(HarvestKind.BREAK_AND_REPLANT, CropFacts(age = 1, ageMax = 2)))
        assertTrue(CropRules.isFullyGrown(HarvestKind.GOURD, CropFacts(attachedStem = true)))
        assertFalse(CropRules.isFullyGrown(HarvestKind.NEVER, wheat))
    }

    @Test
    fun `CD-CUL-03 the Cultivator stops where the Harvester starts`() {
        assertTrue(CropRules.cultivatorDemand(true, HarvestKind.CROP, youngWheat))
        // An oddion of Oh The Biomes We've Gone hatches when bonemeal reaches it at age 3.
        val oddion = CropFacts(bonemealable = true, age = 2, ageMax = 3, adapterTags = setOf(HarvestKind.PICK_TO_AGE_1))
        assertTrue(CropRules.cultivatorDemand(true, HarvestKind.PICK_TO_AGE_1, oddion))
        assertFalse(CropRules.cultivatorDemand(true, HarvestKind.PICK_TO_AGE_1, oddion.copy(age = 3)))
        assertFalse(CropRules.cultivatorDemand(true, HarvestKind.CROP, wheat))
        assertFalse(CropRules.cultivatorDemand(false, HarvestKind.CROP, youngWheat), "an invalid target had demand")
        // A block with no harvest adapter, such as a stem, has demand while bonemeal can change it.
        assertTrue(CropRules.cultivatorDemand(true, null, CropFacts(bonemealable = true, age = 3, ageMax = 7)))
        assertFalse(CropRules.cultivatorDemand(false, null, CropFacts(bonemealable = true, age = 7, ageMax = 7)))
    }
}
