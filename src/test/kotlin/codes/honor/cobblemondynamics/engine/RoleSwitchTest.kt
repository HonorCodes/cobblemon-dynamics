package codes.honor.cobblemondynamics.engine

import codes.honor.cobblemondynamics.claim.LeashIndex
import codes.honor.cobblemondynamics.role.BuiltInRoles
import codes.honor.cobblemondynamics.role.RoleDefinition
import codes.honor.cobblemondynamics.role.RoleSelection
import codes.honor.cobblemondynamics.role.role
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/** R94: the switch roles of a worker on its target and the choice among them, over the default role files and fake lookups. */
class RoleSwitchTest {
    private val all: List<RoleDefinition> by lazy {
        BuiltInRoles.dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().sortedBy { it.name }.map(BuiltInRoles::parse)
    }

    private fun named(path: String): RoleDefinition = all.first { it.id.path == path }

    private val cultivator by lazy { named("cultivator") }
    private val harvester by lazy { named("harvester") }
    private val lumberjack by lazy { named("lumberjack") }
    private val brewStoker by lazy { named("brew_stoker") }
    private val brewer by lazy { named("brewer") }
    private val stoker by lazy { named("stoker") }
    private val telekinesis by lazy { named("telekinesis") }
    private val fanWater by lazy { named("fan_catalyst_water") }

    /** The roles that the types make the worker eligible for, as `RoleSet.forTypes` gives them. */
    private fun enabled(vararg types: String) = all.filter { RoleSelection.isEligible(it, types.toList()) }

    /** The logistics and two-target handlers of the default files. */
    private val single: (RoleDefinition) -> Boolean = { it.handler.path !in setOf("telekinesis", "magnet", "soul_collector") }

    /** A cell that the handlers of [handlers] match and resolve to itself. */
    private fun cell(vararg handlers: String): (RoleDefinition) -> Boolean = { it.handler.path in handlers }

    private fun rolesOf(
        own: RoleDefinition,
        types: Array<String>,
        bound: Boolean,
        matches: (RoleDefinition) -> Boolean,
        itself: (RoleDefinition) -> Boolean = { true },
    ) = RoleSwitch.rolesOf(own, enabled(*types), bound, single, matches, itself)

    @Test
    fun `CD-LSH-33 the switch roles of a bound worker`() {
        val bugGround = arrayOf("bug", "ground")
        val sapling = cell("cultivator", "lumberjack")
        assertEquals(listOf(cultivator, lumberjack), rolesOf(cultivator, bugGround, bound = true, sapling), "Bug/Ground at a sapling")
        val furnace = cell("furnace", "telekinesis")
        assertEquals(listOf(stoker), rolesOf(stoker, arrayOf("fire", "psychic"), bound = true, furnace), "Fire/Psychic at a furnace")
        val fan = cell("fan_catalyst")
        assertEquals(listOf(fanWater), rolesOf(fanWater, arrayOf("water", "fire"), bound = true, fan), "Water/Fire at an encased fan")
        val chest = cell("telekinesis", "furnace")
        assertEquals(emptyList<RoleDefinition>(), rolesOf(telekinesis, arrayOf("psychic", "fire"), bound = true, chest), "Telekinesis")
        // A handler that resolves the cell to another one finds no target of its own there, as the Lumberjack on a sapling of a 2 x 2
        // that is not its corner.
        val notCorner = rolesOf(cultivator, bugGround, bound = true, sapling) { it != lumberjack }
        assertEquals(listOf(cultivator), notCorner)
    }

    @Test
    fun `CD-ASG-22 an unbound worker gets no leash-only switch role`() {
        val sapling = cell("cultivator", "lumberjack")
        assertEquals(listOf(cultivator), rolesOf(cultivator, arrayOf("bug", "ground"), bound = false, sapling))
        val stand = cell("brewing_fuel", "brewing_speed")
        assertEquals(listOf(brewStoker, brewer), rolesOf(brewStoker, arrayOf("poison", "fire"), bound = false, stand))
    }

    private fun choose(
        current: RoleDefinition,
        roles: List<RoleDefinition>,
        types: List<String>,
        demand: Set<RoleDefinition>,
        laneTaken: Set<RoleDefinition> = emptySet(),
        skipped: Set<RoleDefinition> = emptySet(),
    ) = RoleSwitch.choose(current, roles, types, { it in demand }, { it !in laneTaken }, { it in skipped })

    @Test
    fun `CD-LSH-30 a bound worker takes a switch role with demand and keeps its own while that has demand`() {
        val bugGround = listOf("bug", "ground")
        val atSapling = listOf(lumberjack, cultivator)
        assertEquals(cultivator, choose(lumberjack, atSapling, bugGround, setOf(cultivator)), "a Lumberjack in Standby on a sapling")
        val atOak = listOf(cultivator, lumberjack)
        assertEquals(lumberjack, choose(cultivator, atOak, bugGround, setOf(lumberjack)), "a Cultivator on a grown oak")
        assertEquals(lumberjack, choose(lumberjack, atSapling, bugGround, setOf(lumberjack, cultivator)), "its own role has demand")
        assertNull(choose(lumberjack, atSapling, bugGround, emptySet()), "no role has demand")
        // CD-CRP-22: a Torterra bound as the Harvester grows its wheat, and harvests it when grown.
        val torterra = listOf("grass", "ground")
        assertEquals(cultivator, choose(harvester, listOf(harvester, cultivator), torterra, setOf(cultivator)))
        assertEquals(harvester, choose(cultivator, listOf(harvester, cultivator), torterra, setOf(harvester)))
    }

    @Test
    fun `CD-ASG-21 an auto Brew Stoker in Standby at a stand where only the Brewer has demand gets the Brewer`() {
        val stand = listOf(brewStoker, brewer)
        val salazzle = listOf("poison", "fire")
        assertEquals(brewer, choose(brewStoker, stand, salazzle, setOf(brewer)))
        assertEquals(brewStoker, choose(brewStoker, stand, salazzle, setOf(brewStoker, brewer)), "its own role has demand")
        assertNull(choose(brewStoker, stand, salazzle, emptySet()))
    }

    @Test
    fun `CD-LSH-32 a switch role whose lane is held is passed over for the next one, and the binding reserves one lane`() {
        val own = role("own", setOf("bug"), handler = "cobblemon_dynamics:own")
        val first = role("first", setOf("ground"), priority = 5, handler = "cobblemon_dynamics:first")
        val second = role("second", setOf("ground"), handler = "cobblemon_dynamics:second")
        val roles = listOf(own, first, second)
        val types = listOf("bug", "ground")
        assertEquals(first, choose(own, roles, types, setOf(first, second)))
        assertEquals(second, choose(own, roles, types, setOf(first, second), laneTaken = setOf(first)), "another worker holds the lane")
        assertEquals(second, choose(own, roles, types, setOf(first, second), skipped = setOf(first)), "the worker skips that key")
        assertNull(choose(own, roles, types, setOf(first, second), laneTaken = setOf(first, second)))
        // The index keeps the binding's own role, so a Torterra bound as the Harvester reserves the Harvester's lane only.
        val laneOf: (ResourceLocation) -> String? = { it.path.takeIf { path -> path == "cultivator" || path == "harvester" } }
        val index = LeashIndex()
        val crop = BlockPos(5, 64, 5)
        val torterra = LeashIndex.Ref(UUID.randomUUID(), UUID.randomUUID(), BlockPos.ZERO, harvester.id)
        index.put(crop, torterra)
        assertEquals(torterra, index.exclusiveAt(crop, "harvester", { false }, laneOf))
        assertNull(index.exclusiveAt(crop, "cultivator", { false }, laneOf), "the binding reserved a second lane")
    }

    @Test
    fun `CD-LSH-31 a Cultivator binding of a Bug-Ground worker holds on its grown oak, and that of a Ground-type does not`() {
        val oak = cell("lumberjack")
        val bugGround = RoleSwitch.bindingHolds(ownHolds = false) { rolesOf(cultivator, arrayOf("bug", "ground"), bound = true, oak) }
        assertTrue(bugGround, "the Bug/Ground binding cleared")
        val ground = RoleSwitch.bindingHolds(ownHolds = false) { rolesOf(cultivator, arrayOf("ground"), bound = true, oak) }
        assertFalse(ground, "the Ground binding held")
        // A base log that resolves to another cell is no tree of its own there.
        val moved = RoleSwitch.bindingHolds(ownHolds = false) { rolesOf(cultivator, arrayOf("bug", "ground"), true, oak) { false } }
        assertFalse(moved)
        var asked = false
        val held = RoleSwitch.bindingHolds(ownHolds = true) {
            asked = true
            emptyList()
        }
        assertTrue(held)
        assertFalse(asked, "a binding that its own handler keeps asks no switch role")
    }

    /**
     * Review: a switch role that would refuse a bind on the cell, as the Lumberjack refuses a tree over its `max_logs` or `max_leaves`,
     * a tree of Dynamic Trees with no hook or a tree of the never tag, is no switch role, so the binding clears as in 1.2.0.
     */
    @Test
    fun `CD-LSH-31 a binding on a tree that no switch role would fell does not hold`() {
        val oak = cell("lumberjack")
        val types = arrayOf("bug", "ground")
        val refused: (RoleDefinition) -> Boolean = { it != lumberjack }
        val holds = RoleSwitch.bindingHolds(ownHolds = false) {
            RoleSwitch.rolesOf(cultivator, enabled(*types), true, single, oak, { true }, accepts = refused)
        }
        assertFalse(holds, "a refused tree kept the binding")
        val felled = RoleSwitch.bindingHolds(ownHolds = false) {
            RoleSwitch.rolesOf(cultivator, enabled(*types), true, single, oak, { true }, accepts = { true })
        }
        assertTrue(felled)
        // The accept check, which searches the tree, runs last and only for a role that passed every other check.
        val asked = ArrayList<RoleDefinition>()
        RoleSwitch.rolesOf(cultivator, enabled(*types), true, single, oak, { true }) {
            asked += it
            true
        }
        assertEquals(listOf(lumberjack), asked)
    }

    @Test
    fun `CD-ASG-04 an unbound Fairy-type never switches, and a bound one does`() {
        assertFalse(RoleSwitch.maySwitch(bound = false, hasFairyType = true))
        assertTrue(RoleSwitch.maySwitch(bound = true, hasFairyType = true))
        assertTrue(RoleSwitch.maySwitch(bound = false, hasFairyType = false))
    }

    /** Review: the Lumberjack's checks search the whole tree, so their answer lasts until the block at the target changes. */
    @Test
    fun `CD-LSH-33 the switch answer is computed again only when its key changes`() {
        val cache = SwitchCache<Boolean>()
        var computed = 0
        val log = Any()
        for (check in 0 until 100) assertTrue(cache.get(listOf(BlockPos.ZERO, log)) { computed++.let { true } })
        assertEquals(1, computed)
        val sapling = Any()
        assertFalse(cache.get(listOf(BlockPos.ZERO, sapling)) { computed++.let { false } })
        assertFalse(cache.get(listOf(BlockPos.ZERO, sapling)) { computed++.let { true } })
        assertEquals(2, computed)
    }

    @Test
    fun `CD-LSH-34 a slot keeps one memo for each switch role until its binding changes`() {
        val probes = SwitchProbes()
        val key = BlockPos(1, 64, 1) to cultivator.id
        probes.follow(key)
        val lumber = probes.memo(lumberjack.id)
        val grow = probes.memo(cultivator.id)
        assertNotSame(lumber, grow)
        for (poll in 0 until 60) {
            probes.follow(key)
            assertSame(lumber, probes.memo(lumberjack.id), "poll $poll got a new memo")
        }
        probes.follow(BlockPos(2, 64, 1) to cultivator.id)
        assertNotSame(lumber, probes.memo(lumberjack.id), "a new target kept the memo")
        val moved = probes.memo(lumberjack.id)
        probes.follow(BlockPos(2, 64, 1) to lumberjack.id)
        assertNotSame(moved, probes.memo(lumberjack.id), "a new role kept the memo")
        val kept = probes.memo(lumberjack.id)
        probes.clear()
        assertNotSame(kept, probes.memo(lumberjack.id), "a release kept the memo")
    }
}
