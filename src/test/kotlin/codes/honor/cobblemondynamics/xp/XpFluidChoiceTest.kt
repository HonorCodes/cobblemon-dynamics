package codes.honor.cobblemondynamics.xp

import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** The fluids are their ids here, so the choice runs with no fluid registry. */
class XpFluidChoiceTest {
    private val cei = ResourceLocation.parse("create_enchantment_industry:experience")
    private val sophisticated = ResourceLocation.parse("sophisticatedcore:xp_still")
    private val other = ResourceLocation.parse("example:liquid_xp")

    private fun registry(vararg ids: ResourceLocation): (ResourceLocation) -> ResourceLocation? = { id -> id.takeIf { it in ids } }

    @Test
    fun `CD-XPC-08 CEI's fluid gives itself at 1 mB per XP, also when c-experience holds another fluid`() {
        assertEquals(XpFluid(cei, 1), XpFluidChoice.choose(registry(cei), emptyList()))
        assertEquals(XpFluid(cei, 1), XpFluidChoice.choose(registry(cei, sophisticated), listOf(sophisticated)))
    }

    @Test
    fun `CD-XPC-08 sophisticatedcore xp_still alone in the tag gives itself at 20 mB per XP, and the first of two wins`() {
        assertEquals(XpFluid(sophisticated, 20), XpFluidChoice.choose(registry(sophisticated), listOf(sophisticated)))
        assertEquals(XpFluid(other, 20), XpFluidChoice.choose(registry(sophisticated, other), listOf(other, sophisticated)))
    }

    @Test
    fun `CD-XPC-08 with neither, the outputs offer no fluid`() {
        assertNull(XpFluidChoice.choose(registry(), emptyList<ResourceLocation>()))
    }

    @Test
    fun `CD-XPC-08 the INFO line names the choice`() {
        assertEquals(
            "Experience Collector: pipes drain create_enchantment_industry:experience at 1 mB per XP",
            XpFluidChoice.line(XpFluid(cei, 1), cei),
        )
        assertEquals(
            "Experience Collector: pipes drain sophisticatedcore:xp_still at 20 mB per XP",
            XpFluidChoice.line(XpFluid(sophisticated, 20), sophisticated),
        )
        assertEquals("Experience Collector: no XP fluid, so pipes drain nothing", XpFluidChoice.line(null, null))
    }
}
