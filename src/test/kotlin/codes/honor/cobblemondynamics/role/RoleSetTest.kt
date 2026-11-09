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
    fun `forTypes keeps only eligible roles`() {
        val set = RoleSet(mapOf(stoker.id to stoker, wheel.id to wheel), { FakeHandler(it) }, emptySet()) { }
        assertEquals(listOf(stoker.id), set.forTypes(listOf("fire", "flying")).map { it.definition.id })
    }
}
