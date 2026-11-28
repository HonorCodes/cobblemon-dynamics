package codes.honor.cobblemondynamics.pasture

import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class PastureEntityLookupTest {
    private val pasturePos = BlockPos(0, 1, 0)
    private val pokemonId = UUID.randomUUID()
    private val tetheringId = UUID.randomUUID()

    @Test
    fun `CD-NAV-06 prefers a live matching entityId hit without querying the roam box`() {
        var roamQueried = false
        val found = PastureAdapter.resolveTetheredEntity(
            pokemonId = pokemonId,
            tetheringId = tetheringId,
            pasturePos = pasturePos,
            byId = {
                PastureAdapter.EntityCandidate(
                    "direct",
                    alive = true,
                    pokemonId = pokemonId,
                    tetheringId = tetheringId,
                    pasturePos = pasturePos,
                )
            },
            inRoamBox = {
                roamQueried = true
                emptyList()
            },
        )
        assertEquals("direct", found)
        assertFalse(roamQueried)
    }

    @Test
    fun `CD-NAV-06 falls back to roam box when entityId is null, removed or mismatched`() {
        val roam = listOf(
            PastureAdapter.EntityCandidate(
                "roam-hit",
                alive = true,
                pokemonId = pokemonId,
                tetheringId = tetheringId,
                pasturePos = pasturePos,
            ),
        )
        var roamCalls = 0
        val queryRoam = {
            roamCalls++
            roam
        }
        assertEquals("roam-hit", PastureAdapter.resolveTetheredEntity(pokemonId, tetheringId, pasturePos, { null }, queryRoam))
        assertEquals(
            "roam-hit",
            PastureAdapter.resolveTetheredEntity(
                pokemonId,
                tetheringId,
                pasturePos,
                {
                    PastureAdapter.EntityCandidate(
                        "dead",
                        alive = false,
                        pokemonId = pokemonId,
                        tetheringId = tetheringId,
                        pasturePos = pasturePos,
                    )
                },
                queryRoam,
            ),
        )
        assertEquals(
            "roam-hit",
            PastureAdapter.resolveTetheredEntity(
                pokemonId,
                tetheringId,
                pasturePos,
                {
                    PastureAdapter.EntityCandidate(
                        "wrong",
                        alive = true,
                        pokemonId = UUID.randomUUID(),
                        tetheringId = tetheringId,
                        pasturePos = pasturePos,
                    )
                },
                queryRoam,
            ),
        )
        assertEquals(
            "roam-hit",
            PastureAdapter.resolveTetheredEntity(
                pokemonId,
                tetheringId,
                pasturePos,
                {
                    PastureAdapter.EntityCandidate(
                        "wrong-pasture",
                        alive = true,
                        pokemonId = pokemonId,
                        tetheringId = tetheringId,
                        pasturePos = BlockPos(99, 1, 99),
                    )
                },
                queryRoam,
            ),
        )
        assertTrue(roamCalls == 4)
        assertNull(PastureAdapter.resolveTetheredEntity(pokemonId, tetheringId, pasturePos, { null }, { emptyList() }))
    }
}
