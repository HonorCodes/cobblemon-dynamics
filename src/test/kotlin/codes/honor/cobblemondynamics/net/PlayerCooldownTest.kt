package codes.honor.cobblemondynamics.net

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class PlayerCooldownTest {
    private val a = UUID.randomUUID()
    private val b = UUID.randomUUID()

    @Test
    fun `a request within the cooldown of the player's last allowed one is refused, and one at the cooldown is allowed`() {
        val cooldown = PlayerCooldown()
        assertTrue(cooldown.tryAcquire(a, 100, 10))
        assertFalse(cooldown.tryAcquire(a, 109, 10), "a request 9 ticks after the last one was allowed")
        assertTrue(cooldown.tryAcquire(b, 109, 10), "player B waited for player A's cooldown")
        assertTrue(cooldown.tryAcquire(a, 110, 10), "a request 10 ticks after the last one was refused")
    }

    @Test
    fun `a refused request does not restart the cooldown`() {
        val cooldown = PlayerCooldown()
        assertTrue(cooldown.tryAcquire(a, 0, 10))
        for (tick in 1L..9L) assertFalse(cooldown.tryAcquire(a, tick, 10), "a request at tick $tick was allowed")
        assertTrue(cooldown.tryAcquire(a, 10, 10), "the refused requests pushed the cooldown back")
    }

    @Test
    fun `a forgotten player and a cleared cooldown allow the next request at once`() {
        val cooldown = PlayerCooldown()
        assertTrue(cooldown.tryAcquire(a, 0, 10))
        assertTrue(cooldown.tryAcquire(b, 0, 10))
        cooldown.forget(a)
        assertTrue(cooldown.tryAcquire(a, 1, 10), "a player who logged out and back in still waited")
        assertFalse(cooldown.tryAcquire(b, 1, 10), "forgetting player A reset player B")
        cooldown.clear()
        assertTrue(cooldown.tryAcquire(b, 2, 10), "a server stop left a cooldown")
    }
}
