package codes.honor.cobblemondynamics.compat

import codes.honor.cobblemondynamics.OnceLogger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class LandClaimsActorUnionTest {
    private val lines = mutableListOf<String>()
    private val gate = LandClaimGate(OnceLogger(lines::add))
    private val placer = UUID.randomUUID()
    private val pokemonOwner = UUID.randomUUID()
    private val binder = UUID.randomUUID()

    @Test
    fun `CD-TEAM-07 deduplicates non-null actors in order and falls back to single null when all actors are null`() {
        assertEquals(listOf(placer, pokemonOwner, binder), LandClaimActors.candidates(listOf(placer, pokemonOwner, placer, null, binder)))
        assertEquals(listOf(placer), LandClaimActors.candidates(listOf(placer, placer, null)))
        assertEquals(listOf<UUID?>(null), LandClaimActors.candidates(listOf(null, null)))
        assertEquals(listOf<UUID?>(null), LandClaimActors.candidates(emptyList()))
    }

    @Test
    fun `CD-TEAM-07 passes when at least one stakeholder passes every loaded claim mod`() {
        val asked = mutableListOf<Pair<String, UUID?>>()
        val mods = listOf("ftbchunks" to 1, "minecolonies" to 2)
        val allowed = gate.anyActorAllows(ClaimAsker.PASTURE, listOf(placer, pokemonOwner, placer), mods) { mod, actor ->
            asked += (if (mod == 1) "ftb" else "mc") to actor
            actor == pokemonOwner
        }
        assertTrue(allowed)
        assertEquals(listOf("ftb" to placer, "ftb" to pokemonOwner, "mc" to pokemonOwner), asked)
    }

    @Test
    fun `CD-TEAM-07 refuses when every stakeholder fails at least one claim mod`() {
        val mods = listOf("ftbchunks" to 1, "minecolonies" to 2)
        val allowed = gate.anyActorAllows(ClaimAsker.PASTURE, listOf(placer, pokemonOwner, binder), mods) { mod, actor ->
            when (actor) {
                placer -> mod == 1
                pokemonOwner -> mod == 2
                else -> false
            }
        }
        assertFalse(allowed)
    }

    @Test
    fun `CD-TEAM-07 ownerless pasture passes on unclaimed land and refuses on claimed land`() {
        val mods = listOf("ftbchunks" to 1, "openpartiesandclaims" to 2)
        assertFalse(gate.anyActorAllows(ClaimAsker.PASTURE, listOf(null, null), mods) { _, actor -> actor != null })
        assertTrue(gate.anyActorAllows(ClaimAsker.PASTURE, listOf(null, null), mods) { _, _ -> true })
    }

    @Test
    fun `CD-TEAM-07 throwing claim mod refuses all actors and warns once`() {
        val mods = listOf("ftbchunks" to 1)
        assertFalse(
            gate.anyActorAllows(ClaimAsker.HARVEST, listOf(placer, pokemonOwner), mods) { _, _ ->
                throw IllegalStateException("broken")
            },
        )
        assertEquals(1, lines.size)
    }
}
