package codes.honor.cobblemondynamics.handler.sentry

import codes.honor.cobblemondynamics.engine.WorkArea
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.MobSpawnType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SentryRulesTest {
    private val area = WorkArea(BlockPos(10, 64, 10), horizontal = 8, vertical = 6)

    @Test
    fun `CD-SNT-02 cancels natural monster spawns inside workArea and allows spawns 1 block outside`() {
        assertTrue(
            SentryRules.shouldCancel(MobSpawnType.NATURAL, MobCategory.MONSTER, isCobblemon = false, BlockPos(10, 64, 10), listOf(area)),
        )
        assertTrue(
            SentryRules.shouldCancel(MobSpawnType.NATURAL, MobCategory.MONSTER, isCobblemon = false, BlockPos(2, 58, 2), listOf(area)),
        )
        assertTrue(
            SentryRules.shouldCancel(MobSpawnType.NATURAL, MobCategory.MONSTER, isCobblemon = false, BlockPos(18, 70, 18), listOf(area)),
        )
        assertFalse(
            SentryRules.shouldCancel(MobSpawnType.NATURAL, MobCategory.MONSTER, isCobblemon = false, BlockPos(1, 64, 10), listOf(area)),
        )
        assertFalse(
            SentryRules.shouldCancel(MobSpawnType.NATURAL, MobCategory.MONSTER, isCobblemon = false, BlockPos(19, 64, 10), listOf(area)),
        )
        assertFalse(
            SentryRules.shouldCancel(MobSpawnType.NATURAL, MobCategory.MONSTER, isCobblemon = false, BlockPos(10, 57, 10), listOf(area)),
        )
        assertFalse(
            SentryRules.shouldCancel(MobSpawnType.NATURAL, MobCategory.MONSTER, isCobblemon = false, BlockPos(10, 71, 10), listOf(area)),
        )
        assertFalse(
            SentryRules.shouldCancel(MobSpawnType.NATURAL, MobCategory.MONSTER, isCobblemon = false, BlockPos(10, 64, 1), listOf(area)),
        )
        assertFalse(
            SentryRules.shouldCancel(MobSpawnType.NATURAL, MobCategory.MONSTER, isCobblemon = false, BlockPos(10, 64, 19), listOf(area)),
        )
        assertFalse(
            SentryRules.shouldCancel(MobSpawnType.NATURAL, MobCategory.MONSTER, isCobblemon = false, BlockPos(10, 64, 10), emptyList()),
        )
    }

    @Test
    fun `CD-SNT-03 leaves every non-NATURAL spawn type, non-MONSTER category, and Cobblemon spawn untouched`() {
        val center = BlockPos(10, 64, 10)
        for (spawnType in MobSpawnType.entries.filter { it != MobSpawnType.NATURAL }) {
            assertFalse(
                SentryRules.shouldCancel(spawnType, MobCategory.MONSTER, isCobblemon = false, center, listOf(area)),
                "cancelled $spawnType",
            )
        }
        for (category in MobCategory.entries.filter { it != MobCategory.MONSTER }) {
            assertFalse(
                SentryRules.shouldCancel(MobSpawnType.NATURAL, category, isCobblemon = false, center, listOf(area)),
                "cancelled $category",
            )
        }
        assertFalse(
            SentryRules.shouldCancel(MobSpawnType.NATURAL, MobCategory.MONSTER, isCobblemon = true, center, listOf(area)),
            "cancelled Cobblemon",
        )
    }
}
