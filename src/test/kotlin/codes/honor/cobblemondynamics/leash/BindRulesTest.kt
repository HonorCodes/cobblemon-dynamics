package codes.honor.cobblemondynamics.leash

import codes.honor.cobblemondynamics.handler.BindingShape
import codes.honor.cobblemondynamics.role.RoleDefinition
import codes.honor.cobblemondynamics.role.role
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.util.UUID

class BindRulesTest {
    private val stoker = role("stoker", setOf("fire"))
    private val transfer = role("test_transfer", setOf("psychic"), autoAssign = false)
    private val shape: (RoleDefinition) -> BindingShape = {
        if (it == transfer) BindingShape.SOURCE_AND_DESTINATION else BindingShape.SINGLE
    }

    private fun input(
        selection: LeashSelection? = LeashSelection(UUID.randomUUID(), "Charmander"),
        owned: Boolean = true,
        workMode: Boolean = true,
        roles: List<RoleDefinition> = listOf(stoker),
        inside: Boolean = true,
        boundTo: String? = null,
        pending: PendingSource? = null,
        same: Boolean = false,
    ) = BindRules.Input(selection, owned, workMode, roles, BlockPos.ZERO, inside, boundTo, pending, same)

    private fun refusal(input: BindRules.Input): BindRules.Outcome.Refuse =
        assertInstanceOf(BindRules.Outcome.Refuse::class.java, BindRules.evaluate(input, shape))

    @Test
    fun `CD-SEC-08 no server-side selection never binds`() {
        assertEquals("cobblemon_dynamics.leash.no_selection", refusal(input(selection = null)).messageKey)
        assertEquals("cobblemon_dynamics.leash.no_selection", refusal(input(owned = false)).messageKey)
    }

    @Test
    fun `CD-LSH-05 each broken rule refuses with its message`() {
        assertEquals("cobblemon_dynamics.leash.outside_area", refusal(input(inside = false)).messageKey)
        assertEquals("cobblemon_dynamics.leash.no_role", refusal(input(roles = emptyList())).messageKey)
        val assigned = refusal(input(boundTo = "Pikachu"))
        assertEquals("cobblemon_dynamics.leash.already_assigned", assigned.messageKey)
        assertEquals("Pikachu", assigned.arg)
        assertEquals("cobblemon_dynamics.leash.not_work_mode", refusal(input(workMode = false)).messageKey)
    }

    @Test
    fun `CD-LSH-05 the checks run in order, so the first broken rule names the refusal`() {
        assertEquals("cobblemon_dynamics.leash.no_selection", refusal(input(owned = false, workMode = false)).messageKey)
        assertEquals("cobblemon_dynamics.leash.not_work_mode", refusal(input(workMode = false, roles = emptyList())).messageKey)
        assertEquals("cobblemon_dynamics.leash.no_role", refusal(input(roles = emptyList(), inside = false)).messageKey)
        assertEquals("cobblemon_dynamics.leash.outside_area", refusal(input(inside = false, boundTo = "Pikachu")).messageKey)
    }

    @Test
    fun `CD-LSH-03 every rule passing binds`() {
        val bind = assertInstanceOf(BindRules.Outcome.Bind::class.java, BindRules.evaluate(input(), shape))
        assertEquals(stoker, bind.role)
        assertEquals(BlockPos.ZERO, bind.canonical)
    }

    @Test
    fun `CD-LSH-16 the role with the higher priority binds`() {
        val superheater = role("superheater", setOf("fire"), priority = 10)
        val outcome = BindRules.evaluate(input(roles = listOf(stoker, superheater)), shape)
        assertEquals(superheater, assertInstanceOf(BindRules.Outcome.Bind::class.java, outcome).role)
    }

    @Test
    fun `CD-LSH-17 a source-and-destination role waits for the destination`() {
        assertInstanceOf(BindRules.Outcome.AwaitDestination::class.java, BindRules.evaluate(input(roles = listOf(transfer)), shape))
    }

    @Test
    fun `CD-LSH-17 a source outside the work area or bound to another Pokemon is refused at the first click`() {
        assertEquals("cobblemon_dynamics.leash.outside_area", refusal(input(roles = listOf(transfer), inside = false)).messageKey)
        assertEquals("cobblemon_dynamics.leash.already_assigned", refusal(input(roles = listOf(transfer), boundTo = "Abra")).messageKey)
    }

    @Test
    fun `CD-LSH-18 the second click binds source and destination`() {
        val pending = PendingSource(OVERWORLD, BlockPos(1, 0, 0), Direction.UP, 0)
        assertInstanceOf(BindRules.Outcome.Bind::class.java, BindRules.evaluate(input(roles = listOf(transfer), pending = pending), shape))
    }

    @Test
    fun `CD-LSH-18 a destination equal to the source is refused`() {
        val pending = PendingSource(OVERWORLD, BlockPos.ZERO, Direction.UP, 0)
        assertEquals(
            "cobblemon_dynamics.leash.destination_same",
            refusal(input(roles = listOf(transfer), pending = pending, same = true)).messageKey,
        )
    }

    private companion object {
        val OVERWORLD: ResourceLocation = ResourceLocation.withDefaultNamespace("overworld")
    }
}
