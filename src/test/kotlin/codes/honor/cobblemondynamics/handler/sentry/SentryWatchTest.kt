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

        SentryWatch.watch(firstKey, firstArea)
        SentryWatch.watch(secondKey, secondArea)

        assertTrue(SentryWatch.shouldCancel(overworld, onlyFirst, MobSpawnType.NATURAL, MobCategory.MONSTER, isCobblemon = false))
        assertTrue(SentryWatch.shouldCancel(overworld, overlap, MobSpawnType.NATURAL, MobCategory.MONSTER, isCobblemon = false))
        assertTrue(SentryWatch.shouldCancel(overworld, onlySecond, MobSpawnType.NATURAL, MobCategory.MONSTER, isCobblemon = false))
        assertFalse(SentryWatch.shouldCancel(nether, overlap, MobSpawnType.NATURAL, MobCategory.MONSTER, isCobblemon = false))

        SentryWatch.unwatch(firstKey)
        assertFalse(SentryWatch.shouldCancel(overworld, onlyFirst, MobSpawnType.NATURAL, MobCategory.MONSTER, isCobblemon = false))
        assertTrue(SentryWatch.shouldCancel(overworld, overlap, MobSpawnType.NATURAL, MobCategory.MONSTER, isCobblemon = false))
        assertTrue(SentryWatch.shouldCancel(overworld, onlySecond, MobSpawnType.NATURAL, MobCategory.MONSTER, isCobblemon = false))

        SentryWatch.unwatch(secondKey)
        assertFalse(SentryWatch.shouldCancel(overworld, overlap, MobSpawnType.NATURAL, MobCategory.MONSTER, isCobblemon = false))
        assertFalse(SentryWatch.shouldCancel(overworld, onlySecond, MobSpawnType.NATURAL, MobCategory.MONSTER, isCobblemon = false))
    }

    @Test
    fun `CD-SNT-04 reset clears every active watch`() {
        val key = PastureKey(overworld, BlockPos(0, 64, 0).asLong())
        SentryWatch.watch(key, WorkArea(BlockPos(0, 64, 0), horizontal = 8, vertical = 6))
        assertTrue(SentryWatch.shouldCancel(overworld, BlockPos(0, 64, 0), MobSpawnType.NATURAL, MobCategory.MONSTER, isCobblemon = false))

        SentryWatch.reset()
        assertFalse(SentryWatch.shouldCancel(overworld, BlockPos(0, 64, 0), MobSpawnType.NATURAL, MobCategory.MONSTER, isCobblemon = false))
    }
}
