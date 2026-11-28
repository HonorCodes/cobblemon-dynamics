package codes.honor.cobblemondynamics.stamina

import codes.honor.cobblemondynamics.config.EngineSettings
import com.cobblemon.mod.common.pokemon.Gender
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class MateBonusTest {
    private fun sp(name: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath("cobblemon", name)

    private fun cand(species: String, gender: Gender): MateCandidate = MateCandidate(UUID.randomUUID(), sp(species), gender)

    @Test
    fun `CD-PRG-07 same-species male and female and all canon counterpart pairs receive MATE`() {
        val mPika = cand("pikachu", Gender.MALE)
        val fPika = cand("pikachu", Gender.FEMALE)
        assertEquals(setOf(mPika.pokemonId, fPika.pokemonId), WorkTraits.matedWorkers(listOf(mPika, fPika)))

        val nidoking = cand("nidoking", Gender.MALE)
        val nidoqueen = cand("nidoqueen", Gender.FEMALE)
        assertEquals(setOf(nidoking.pokemonId, nidoqueen.pokemonId), WorkTraits.matedWorkers(listOf(nidoking, nidoqueen)))

        val nidoranM = cand("nidoranm", Gender.MALE)
        val nidorina = cand("nidorina", Gender.FEMALE)
        assertEquals(setOf(nidoranM.pokemonId, nidorina.pokemonId), WorkTraits.matedWorkers(listOf(nidoranM, nidorina)))

        val volbeat = cand("volbeat", Gender.MALE)
        val illumise = cand("illumise", Gender.FEMALE)
        assertEquals(setOf(volbeat.pokemonId, illumise.pokemonId), WorkTraits.matedWorkers(listOf(volbeat, illumise)))

        val tauros = cand("tauros", Gender.MALE)
        val miltank = cand("miltank", Gender.FEMALE)
        assertEquals(setOf(tauros.pokemonId, miltank.pokemonId), WorkTraits.matedWorkers(listOf(tauros, miltank)))

        val eval = WorkTraits.evaluate(hasMate = true, settings = EngineSettings.DEFAULT)
        assertEquals(1.15, eval.combinedMultiplier, 1e-9)
        assertTrue(eval.hasMate)
        assertEquals(TraitFlags.MATE, eval.flags)
    }

    @Test
    fun `CD-PRG-07 same-gender, genderless, and separate pastures do not receive MATE`() {
        val m1 = cand("pikachu", Gender.MALE)
        val m2 = cand("pikachu", Gender.MALE)
        assertTrue(WorkTraits.matedWorkers(listOf(m1, m2)).isEmpty())

        val g1 = cand("magnemite", Gender.GENDERLESS)
        val g2 = cand("magnemite", Gender.GENDERLESS)
        assertTrue(WorkTraits.matedWorkers(listOf(g1, g2)).isEmpty())

        val pastureA = WorkTraits.matedWorkers(listOf(cand("pikachu", Gender.MALE)))
        val pastureB = WorkTraits.matedWorkers(listOf(cand("pikachu", Gender.FEMALE)))
        assertTrue(pastureA.isEmpty())
        assertTrue(pastureB.isEmpty())
        assertFalse(WorkTraits.evaluate(hasMate = false).hasMate)
    }
}
