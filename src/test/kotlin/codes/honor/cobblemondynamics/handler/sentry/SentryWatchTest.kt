package codes.honor.cobblemondynamics.handler.sentry

import codes.honor.cobblemondynamics.engine.WorkArea
import codes.honor.cobblemondynamics.pasture.PastureKey
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.MobSpawnType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SentryWatchTest {
    private val overworld = ResourceLocation.withDefaultNamespace("overworld")
    private val nether = ResourceLocation.withDefaultNamespace("the_nether")

    @AfterEach
    fun resetWatch() {
        SentryWatch.reset()
    }

    @Test
    fun `CD-SNT-04 unwatch removes one pasture while retaining an overlapping active pasture`() {
        val firstPos = BlockPos(0, 64, 0)
        val secondPos = BlockPos(10, 64, 0)
        val firstKey = PastureKey(overworld, firstPos.asLong())
        val secondKey = PastureKey(overworld, secondPos.asLong())
        val firstArea = WorkArea(firstPos, horizontal = 8, vertical = 6)
        val secondArea = WorkArea(secondPos, horizontal = 8, vertical = 6)
        val onlyFirst = BlockPos(-5, 64, 0)
        val overlap = BlockPos(5, 64, 0)
        val onlySecond = BlockPos(15, 64, 0)

        SentryWatch.watch(firstKey, firstArea, now = 100L)
        SentryWatch.watch(secondKey, secondArea, now = 100L)

        assertTrue(
            SentryWatch.shouldCancel(overworld, onlyFirst, MobSpawnType.NATURAL, MobCategory.MONSTER, isCobblemon = false, now = 100L),
        )
        assertTrue(SentryWatch.shouldCancel(overworld, overlap, MobSpawnType.NATURAL, MobCategory.MONSTER, isCobblemon = false, now = 101L))
        assertTrue(
            SentryWatch.shouldCancel(overworld, onlySecond, MobSpawnType.NATURAL, MobCategory.MONSTER, isCobblemon = false, now = 100L),
        )
        assertFalse(SentryWatch.shouldCancel(nether, overlap, MobSpawnType.NATURAL, MobCategory.MONSTER, isCobblemon = false, now = 100L))

        SentryWatch.unwatch(firstKey)
        assertFalse(
            SentryWatch.shouldCancel(overworld, onlyFirst, MobSpawnType.NATURAL, MobCategory.MONSTER, isCobblemon = false, now = 100L),
        )
        assertTrue(SentryWatch.shouldCancel(overworld, overlap, MobSpawnType.NATURAL, MobCategory.MONSTER, isCobblemon = false, now = 100L))
        assertTrue(
            SentryWatch.shouldCancel(overworld, onlySecond, MobSpawnType.NATURAL, MobCategory.MONSTER, isCobblemon = false, now = 100L),
        )

        SentryWatch.unwatch(secondKey)
        assertFalse(
            SentryWatch.shouldCancel(overworld, overlap, MobSpawnType.NATURAL, MobCategory.MONSTER, isCobblemon = false, now = 100L),
        )
        assertFalse(
            SentryWatch.shouldCancel(overworld, onlySecond, MobSpawnType.NATURAL, MobCategory.MONSTER, isCobblemon = false, now = 100L),
        )
    }

    @Test
    fun `CD-SNT-04 unrenewed watch lapses after 1 tick if teardown is bypassed and reset clears immediately`() {
        val pos = BlockPos(0, 64, 0)
        val key = PastureKey(overworld, pos.asLong())
        SentryWatch.watch(key, WorkArea(pos, horizontal = 8, vertical = 6), now = 50L)
        assertTrue(SentryWatch.shouldCancel(overworld, pos, MobSpawnType.NATURAL, MobCategory.MONSTER, isCobblemon = false, now = 50L))
        assertTrue(SentryWatch.shouldCancel(overworld, pos, MobSpawnType.NATURAL, MobCategory.MONSTER, isCobblemon = false, now = 51L))
        assertFalse(SentryWatch.shouldCancel(overworld, pos, MobSpawnType.NATURAL, MobCategory.MONSTER, isCobblemon = false, now = 52L))

        SentryWatch.watch(key, WorkArea(pos, horizontal = 8, vertical = 6), now = 60L)
        SentryWatch.reset()
        assertFalse(SentryWatch.shouldCancel(overworld, pos, MobSpawnType.NATURAL, MobCategory.MONSTER, isCobblemon = false, now = 60L))
    }
}
