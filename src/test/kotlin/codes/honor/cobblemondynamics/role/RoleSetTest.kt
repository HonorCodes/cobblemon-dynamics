package codes.honor.cobblemondynamics.role

import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RoleSetTest {
    private val stoker = role("stoker", setOf("fire"), handler = "cobblemon_dynamics:furnace")
    private val wheel = role("wheel_driver", setOf("water"), handler = "cobblemon_dynamics:water_wheel")

    @Test
    fun `CD-CFG-10 a role whose handler is missing is disabled with one line that names it`() {
        val lines = mutableListOf<String>()
        val furnaceOnly = { id: ResourceLocation -> if (id.path == "furnace") FakeHandler(id) else null }
        val set = RoleSet(mapOf(stoker.id to stoker, wheel.id to wheel), furnaceOnly, emptySet(), lines::add)
        assertEquals(listOf(stoker.id), set.active.map { it.definition.id })
        assertEquals(1, lines.size)
        assertEquals(true, lines.single().contains("cobblemon_dynamics:wheel_driver"))
    }

    @Test
    fun `CD-CFG-05 a role disabled in the config is inactive`() {
        val set = RoleSet(mapOf(stoker.id to stoker), { FakeHandler(it) }, setOf(stoker.id)) { }
        assertNull(set.get(stoker.id))
    }

    @Test
    fun `CD-CMP-10 disabled names a role of the config list and a role with no handler, each with its reason`() {
        val furnaceOnly = { id: ResourceLocation -> if (id.path == "furnace") FakeHandler(id) else null }
        val muse = role("muse", setOf("fairy"), autoAssign = false, handler = "cobblemon_dynamics:enchanting")
        val set = RoleSet(mapOf(stoker.id to stoker, wheel.id to wheel, muse.id to muse), furnaceOnly, setOf(muse.id)) { }
        val expected = mapOf(
            muse.id to "roles.disabled",
            wheel.id to "handler cobblemon_dynamics:water_wheel is not registered",
        )
        assertEquals(expected, set.disabled)
    }

    @Test
    fun `forTypes keeps only eligible roles`() {
        val set = RoleSet(mapOf(stoker.id to stoker, wheel.id to wheel), { FakeHandler(it) }, emptySet()) { }
        assertEquals(listOf(stoker.id), set.forTypes(listOf("fire", "flying")).map { it.definition.id })
    }

    @Test
    fun `CD-PCK-03 CD-PCK-04 forTypes asks the all-of rule, and forLeash leaves out a role that no leash binds`() {
        val picker = role("picker", setOf("bug", "flying"), handler = "cobblemon_dynamics:picker").copy(allTypes = true, leash = false)
        val lumberjack = role("lumberjack", setOf("bug"), autoAssign = false, handler = "cobblemon_dynamics:lumberjack")
        val set = RoleSet(mapOf(picker.id to picker, lumberjack.id to lumberjack), { FakeHandler(it) }, emptySet()) { }
        assertEquals(listOf(lumberjack.id, picker.id), set.forTypes(listOf("bug", "flying")).map { it.definition.id }, "Butterfree")
        assertEquals(listOf(lumberjack.id), set.forTypes(listOf("bug")).map { it.definition.id }, "Caterpie")
        assertEquals(emptyList<ResourceLocation>(), set.forTypes(listOf("flying")).map { it.definition.id }, "Rookidee")
        assertEquals(listOf(lumberjack.id), set.forLeash(listOf("bug", "flying")).map { it.definition.id }, "Butterfree with a leash")
    }
}
