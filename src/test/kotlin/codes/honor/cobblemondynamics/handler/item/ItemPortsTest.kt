package codes.honor.cobblemondynamics.handler.item

import com.google.gson.JsonParser
import net.minecraft.core.Direction
import net.minecraft.world.Container
import net.minecraft.world.WorldlyContainer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.items.wrapper.InvWrapper
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** The fallback of the logistics roles to a plain vanilla `Container` (CD-MCH-10) and its deny tag (CD-MCH-11). */
class ItemPortsTest {
    /** A container of 3 slots whose items no test reads, so that no item stack is made with no game loaded. */
    private open class TestContainer : Container {
        override fun getContainerSize(): Int = 3

        override fun isEmpty(): Boolean = true

        override fun getItem(slot: Int): ItemStack = error("not read")

        override fun removeItem(slot: Int, amount: Int): ItemStack = error("not read")

        override fun removeItemNoUpdate(slot: Int): ItemStack = error("not read")

        override fun setItem(slot: Int, stack: ItemStack): Unit = error("not read")

        override fun setChanged() {}

        override fun stillValid(player: Player): Boolean = true

        override fun clearContent() {}
    }

    /** A sided container that shows slot 2 on its top face only, as a furnace shows its input. */
    private class TestWorldlyContainer :
        TestContainer(),
        WorldlyContainer {
        override fun getSlotsForFace(side: Direction): IntArray = if (side == Direction.UP) intArrayOf(2) else IntArray(0)

        override fun canPlaceItemThroughFace(slot: Int, stack: ItemStack, side: Direction?): Boolean = true

        override fun canTakeItemThroughFace(slot: Int, stack: ItemStack, side: Direction): Boolean = true
    }

    @Test
    fun `CD-MCH-10 a test Container with no capability gives a port through InvWrapper`() {
        val container = TestContainer()
        assertTrue(ContainerFallback.reaches(container, denied = false))
        for (face in listOf(Direction.NORTH, null)) {
            val handler = ContainerFallback.handler(container, denied = false, face = face)
            assertTrue(handler is InvWrapper, "the handler on face $face is $handler")
            assertSame(container, (handler as InvWrapper).inv)
            assertEquals(3, handler.slots)
        }
    }

    @Test
    fun `CD-MCH-10 a WorldlyContainer gives a port through SidedInvWrapper on the face, and none with no face`() {
        val container = TestWorldlyContainer()
        val top = ContainerFallback.handler(container, denied = false, face = Direction.UP)
        assertTrue(top is SidedInvWrapper, "the handler on the top face is $top")
        assertEquals(1, top!!.slots, "the top face shows one slot")
        assertEquals(0, ContainerFallback.handler(container, denied = false, face = Direction.NORTH)!!.slots)
        assertNull(ContainerFallback.handler(container, denied = false, face = null), "a sided container's slots need a face")
    }

    @Test
    fun `CD-MCH-11 a tagged Container gives no port`() {
        assertFalse(ContainerFallback.reaches(TestContainer(), denied = true))
        assertNull(ContainerFallback.handler(TestContainer(), denied = true, face = Direction.NORTH))
        assertNull(ContainerFallback.handler(TestWorldlyContainer(), denied = true, face = Direction.UP))
    }

    @Test
    fun `CD-MCH-10 a block entity that is no Container gives no port`() {
        assertFalse(ContainerFallback.reaches(Any(), denied = false))
        assertNull(ContainerFallback.handler(Any(), denied = false, face = Direction.NORTH))
        assertNull(ContainerFallback.handler(null, denied = false, face = Direction.NORTH))
    }

    /**
     * CD-MCH-11: the ids that Task 19 read from Lootr 1.11.38.127 and Legendary Monuments 8.2, whose block entities are vanilla
     * `Container`s with no item capability, and the Ender Chest, which holds each player's own items. Each entry of another mod is
     * optional (CD-OPT-12).
     */
    @Test
    fun `CD-MCH-11 the deny tag holds Lootr's containers, Legendary Monuments' pedestals and the Ender Chest`() {
        val tags = File(System.getProperty("cd.projectDir"), "src/main/resources/data/cobblemon_dynamics/tags/block")
        val json = JsonParser.parseString(File(tags, "logistics/no_fallback.json").readText()).asJsonObject
        val values = json.getAsJsonArray("values").map { value ->
            if (value.isJsonPrimitive) {
                value.asString to true
            } else {
                value.asJsonObject["id"].asString to (value.asJsonObject["required"]?.asBoolean ?: true)
            }
        }
        val lootr = listOf("lootr_chest", "lootr_trapped_chest", "lootr_barrel", "lootr_shulker", "lootr_inventory", "decorated_pot")
        val pedestals = listOf(
            "pedestal", "steed_pedestal", "dialga_pedestal", "entei_pedestal", "giratina_pedestal", "heatran_pedestal", "ho_oh_pedestal",
            "hoopa_pedestal", "kyurem_pedestal", "latias_pedestal", "lugia_pedestal", "mew_pedestal", "palkia_pedestal",
            "raikou_pedestal", "reshiram_pedestal", "suicune_pedestal", "zacian_pedestal", "zamazenta_pedestal", "zekrom_pedestal",
        )
        val expected = listOf("minecraft:ender_chest" to true) + lootr.map { "lootr:$it" to false } +
            pedestals.map { "legendarymonuments:$it" to false }
        assertEquals(expected.toSet(), values.toSet())
        assertEquals(values.size, values.toSet().size, "the tag names an entry twice")
    }
}
