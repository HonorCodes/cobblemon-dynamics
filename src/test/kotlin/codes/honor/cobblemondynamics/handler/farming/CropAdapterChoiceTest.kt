package codes.honor.cobblemondynamics.handler.farming

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The facts of each family come from the adapter table of ADR-0015, which RES8 read with `javap`. */
class CropAdapterChoiceTest {
    private val wheat = CropFacts(cropBlock = true, bonemealable = true, age = 7, ageMax = 7, cropMaxAge = true)

    @Test
    fun `CD-CRP-03 an adapter tag comes before the class`() {
        assertEquals(HarvestKind.CROP, CropAdapters.harvestKind(wheat))
        // Farmer's Delight tomatoes and Pam's fruit are CropBlocks that a pick harvests.
        assertEquals(HarvestKind.PICK_TO_AGE_0, CropAdapters.harvestKind(wheat.copy(adapterTags = setOf(HarvestKind.PICK_TO_AGE_0))))
        // Hearty grains and hemp are CropBlocks whose upper half is the harvest.
        assertEquals(HarvestKind.UPPER_HALF, CropAdapters.harvestKind(wheat.copy(adapterTags = setOf(HarvestKind.UPPER_HALF))))
    }

    @Test
    fun `CD-CRP-03 the adapter tags keep their order`() {
        val both = wheat.copy(adapterTags = setOf(HarvestKind.BREAK_AND_REPLANT, HarvestKind.PICK_TO_AGE_1))
        assertEquals(HarvestKind.PICK_TO_AGE_1, CropAdapters.harvestKind(both))
        val pickAndHalf = wheat.copy(adapterTags = setOf(HarvestKind.UPPER_HALF, HarvestKind.PICK_TO_AGE_0))
        assertEquals(HarvestKind.PICK_TO_AGE_0, CropAdapters.harvestKind(pickAndHalf))
    }

    @Test
    fun `CD-CRP-03 the class adapters`() {
        val berryTree = CropFacts(berryTree = true, bonemealable = true, age = 5, ageMax = 5)
        assertEquals(HarvestKind.BERRY_TREE, CropAdapters.harvestKind(berryTree))
        assertEquals(HarvestKind.CAVE_VINES, CropAdapters.harvestKind(CropFacts(caveVines = true, bonemealable = true, berries = true)))
        assertEquals(HarvestKind.GOURD, CropAdapters.harvestKind(CropFacts(attachedStem = true)))
        // Cocoa and nether wart are no CropBlocks, so their tag gives them the break and the replant.
        val cocoa = CropFacts(bonemealable = true, age = 2, ageMax = 2, adapterTags = setOf(HarvestKind.BREAK_AND_REPLANT))
        assertEquals(HarvestKind.BREAK_AND_REPLANT, CropAdapters.harvestKind(cocoa))
    }

    @Test
    fun `CD-CRP-04 CD-HRV-02 a stem in harvest-never and a plain bush get no harvest adapter`() {
        assertNull(CropAdapters.harvestKind(CropFacts(bonemealable = true, age = 3, ageMax = 7, adapterTags = setOf(HarvestKind.NEVER))))
        assertNull(CropAdapters.harvestKind(CropFacts(bonemealable = true, age = 3, ageMax = 3)))
        // harvest/never wins over the class, as for the flower crops, which are CropBlocks.
        assertNull(CropAdapters.harvestKind(wheat.copy(adapterTags = setOf(HarvestKind.NEVER, HarvestKind.PICK_TO_AGE_0))))
    }

    @Test
    fun `CD-HRV-03 an apricorn gets no harvest adapter, also in a pick tag`() {
        val apricorn = CropFacts(apricorn = true, bonemealable = true, age = 3, ageMax = 3, adapterTags = setOf(HarvestKind.PICK_TO_AGE_1))
        assertNull(CropAdapters.harvestKind(apricorn))
    }

    @Test
    fun `CD-CUL-02 CD-CRP-04 the growth adapter takes bonemealable blocks only`() {
        assertTrue(CropAdapters.grows(wheat))
        assertTrue(CropAdapters.grows(CropFacts(bonemealable = true, age = 1, ageMax = 3))) // a sweet berry bush
        assertFalse(CropAdapters.grows(CropFacts(age = 1, ageMax = 3))) // nether wart
        assertFalse(CropAdapters.grows(CropFacts(attachedStem = true))) // an attached stem
    }
}
