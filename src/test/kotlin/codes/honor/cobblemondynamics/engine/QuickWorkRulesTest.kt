package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.handler.BindingShape
import codes.honor.cobblemondynamics.role.BuiltInRoles
import codes.honor.cobblemondynamics.role.RoleDefinition
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class QuickWorkRulesTest {
    private val stoker = BuiltInRoles.parse("stoker")
    private val brewStoker = BuiltInRoles.parse("brew_stoker")
    private val brewer = BuiltInRoles.parse("brewer")
    private val telekinesis = BuiltInRoles.parse("telekinesis")
    private val pump = BuiltInRoles.parse("pump")
    private val wheelDriver = BuiltInRoles.parse("wheel_driver")

    /** The handlers of the default role files: a leash-only handler has the scan kind NONE, and Telekinesis names two targets. */
    private fun orderable(role: RoleDefinition): Boolean = QuickWorkRules.mayOrder(
        role,
        scanned = role.handler.path !in setOf("fluid_fill", "telekinesis"),
        shape = if (role.handler.path == "telekinesis") BindingShape.SOURCE_AND_DESTINATION else BindingShape.SINGLE,
    )

    private fun pick(fitting: List<RoleDefinition>, types: List<String>, fairy: Boolean = false) =
        QuickWorkRules.pick(fitting, types, fairy, ::orderable)

    private val calm = QuickWorkRules.Watch(
        entityGone = false,
        battling = false,
        ownerPresent = true,
        ownerDistanceSqr = 100.0,
        leashRange = 16,
        holdsClaim = true,
        status = WorkerStatus.WORKING,
        ticksInStatus = 0,
        standbyLimit = 200,
    )

    @Test
    fun `CD-QWK-03 the order gets the role that an auto claim gets`() {
        assertEquals(QuickWorkRules.Pick.Order(brewStoker), pick(listOf(brewer, brewStoker), listOf("poison", "fire")))
        assertEquals(QuickWorkRules.Pick.Order(stoker), pick(listOf(stoker), listOf("fire")))
    }

    @Test
    fun `CD-QWK-03 CD-QWK-04 a leash-only or two-target role gives no order but the Work Leash message`() {
        assertEquals(QuickWorkRules.Pick.NeedsLeash, pick(listOf(telekinesis), listOf("psychic")))
        assertEquals(QuickWorkRules.Pick.NeedsLeash, pick(listOf(pump), listOf("water")))
        val autoTelekinesis = telekinesis.copy(autoAssign = true)
        assertFalse(QuickWorkRules.mayOrder(autoTelekinesis, scanned = true, shape = BindingShape.SOURCE_AND_DESTINATION))
    }

    @Test
    fun `CD-QWK-05 a Fairy-type is a healer and gets no order`() =
        assertEquals(QuickWorkRules.Pick.None, pick(listOf(wheelDriver), listOf("water", "fairy"), fairy = true))

    @Test
    fun `CD-QWK-03 a block that no role fits gives nothing`() = assertEquals(QuickWorkRules.Pick.None, pick(emptyList(), listOf("fire")))

    @Test
    fun `CD-QWK-06 to CD-QWK-09 the refusals come in their order, and the demand is asked last`() {
        var asked = 0
        val noDemand = {
            asked++
            false
        }
        // The arguments: mayUse, tired, targetFree, hasDemand.
        assertEquals(QuickWorkRules.NO_PERMISSION, QuickWorkRules.refusal(false, true, false, noDemand))
        assertEquals(QuickWorkRules.TIRED, QuickWorkRules.refusal(true, true, false, noDemand))
        assertEquals(QuickWorkRules.CLAIMED, QuickWorkRules.refusal(true, false, false, noDemand))
        assertEquals(0, asked)
        assertEquals(QuickWorkRules.NO_DEMAND, QuickWorkRules.refusal(true, false, true, noDemand))
        assertNull(QuickWorkRules.refusal(mayUse = true, tired = false, targetFree = true) { true })
    }

    @Test
    fun `CD-CFG-16 CD-CFG-18 CD-QWK-21 the trace reaches the smaller range, and none while off or in a battle`() {
        assertEquals(16, QuickWorkRules.traceRange(enabled = true, ownerBattling = false, targetRange = 16, leashRange = 16))
        assertEquals(8, QuickWorkRules.traceRange(enabled = true, ownerBattling = false, targetRange = 24, leashRange = 8))
        assertNull(QuickWorkRules.traceRange(enabled = false, ownerBattling = false, targetRange = 16, leashRange = 16))
        assertNull(QuickWorkRules.traceRange(enabled = true, ownerBattling = true, targetRange = 16, leashRange = 16))
    }

    @Test
    fun `CD-QWK-13 the order ends past the leash and not at it`() {
        assertNull(QuickWorkRules.endReason(calm.copy(ownerDistanceSqr = 256.0)))
        assertEquals(QuickEnd.TOO_FAR, QuickWorkRules.endReason(calm.copy(ownerDistanceSqr = 256.01)))
    }

    @Test
    fun `CD-QWK-16 CD-QWK-18 CD-QWK-20 CD-QWK-21 each end has its reason`() {
        assertEquals(QuickEnd.RECALLED, QuickWorkRules.endReason(calm.copy(entityGone = true)))
        assertEquals(QuickEnd.BATTLE, QuickWorkRules.endReason(calm.copy(battling = true)))
        assertEquals(QuickEnd.OWNER_LEFT, QuickWorkRules.endReason(calm.copy(ownerPresent = false)))
        assertEquals(QuickEnd.TARGET, QuickWorkRules.endReason(calm.copy(holdsClaim = false)))
        assertNull(QuickWorkRules.endReason(calm))
    }

    @Test
    fun `CD-QWK-16 a gone entity wins over every other end`() = assertEquals(
        QuickEnd.RECALLED,
        QuickWorkRules.endReason(calm.copy(entityGone = true, battling = true, ownerPresent = false, holdsClaim = false)),
    )

    @Test
    fun `CD-QWK-19 the Standby time ends the order at the limit and not before`() {
        val standby = calm.copy(status = WorkerStatus.STANDBY)
        assertNull(QuickWorkRules.endReason(standby.copy(ticksInStatus = 199)))
        assertEquals(QuickEnd.NO_DEMAND, QuickWorkRules.endReason(standby.copy(ticksInStatus = 200)))
        assertNull(QuickWorkRules.endReason(calm.copy(ticksInStatus = 5_000)))
    }

    @Test
    fun `CD-QWK-13 the start and the end of an order measure the leash alike`() {
        assertFalse(QuickWorkRules.beyondLeash(ownerDistanceSqr = 256.0, leashRange = 16))
        assertTrue(QuickWorkRules.beyondLeash(ownerDistanceSqr = 256.01, leashRange = 16))
    }

    @Test
    fun `CD-QWK-25 an aim starts an order for AIM_TICKS, and a new aim drops every older one`() {
        assertFalse(QuickWorkRules.isExpired(aimTick = 1_000, now = 1_100))
        assertTrue(QuickWorkRules.isExpired(aimTick = 1_000, now = 1_101))
        val stale = UUID.randomUUID()
        val fresh = UUID.randomUUID()
        val aims = hashMapOf(stale to aim(tick = 1_000), fresh to aim(tick = 1_050))
        QuickWorkRules.dropExpired(aims, now = 1_101)
        assertEquals(setOf(fresh), aims.keys)
    }

    private fun aim(tick: Long) = QuickAim(UUID.randomUUID(), ResourceLocation.withDefaultNamespace("overworld"), BlockPos(0, 64, 0), tick)

    @Test
    fun `CD-QWK-29 an order asks its permission again on every 20th tick after its start`() {
        assertFalse(QuickWorkRules.permissionDue(startedAt = 100, now = 100))
        assertFalse(QuickWorkRules.permissionDue(startedAt = 100, now = 119))
        assertTrue(QuickWorkRules.permissionDue(startedAt = 100, now = 120))
        assertFalse(QuickWorkRules.permissionDue(startedAt = 100, now = 121))
        assertTrue(QuickWorkRules.permissionDue(startedAt = 100, now = 140))
    }

    @Test
    fun `CD-UI-12 each end has its own message key, a timeout reuses the leash's and a lost permission the refusal's`() {
        assertEquals(QuickWorkRules.CANNOT_REACH, QuickEnd.UNREACHABLE.messageKey)
        assertEquals(QuickWorkRules.NO_PERMISSION, QuickEnd.PERMISSION.messageKey)
        assertEquals(QuickEnd.entries.size, QuickEnd.entries.map { it.messageKey }.toSet().size)
    }
}
