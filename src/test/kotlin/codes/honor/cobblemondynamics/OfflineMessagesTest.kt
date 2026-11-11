package codes.honor.cobblemondynamics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class OfflineMessagesTest {
    private val a = UUID.randomUUID()
    private val b = UUID.randomUUID()

    @Test
    fun `CD-LSH-13 an offline owner gets the 10 newest messages at login, oldest first, and only once`() {
        val queue = OfflineMessages<String>()
        for (n in 1..12) queue.add(a, "lost $n")
        queue.add(b, "lost b")
        assertEquals((3..12).map { "lost $it" }, queue.take(a))
        assertEquals(emptyList<String>(), queue.take(a), "a second login got the messages again")
        assertEquals(listOf("lost b"), queue.take(b), "player A's messages pushed out player B's")
    }

    @Test
    fun `CD-LSH-13 a server stop drops every waiting message`() {
        val queue = OfflineMessages<String>()
        queue.add(a, "lost 1")
        queue.add(b, "lost 2")
        queue.clear()
        assertEquals(emptyList<String>(), queue.take(a))
        assertEquals(emptyList<String>(), queue.take(b))
    }
}
