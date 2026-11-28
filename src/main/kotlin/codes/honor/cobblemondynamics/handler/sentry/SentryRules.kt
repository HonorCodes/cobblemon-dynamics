package codes.honor.cobblemondynamics.handler.sentry

import codes.honor.cobblemondynamics.engine.WorkArea
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.MobSpawnType

object SentryRules {
    fun shouldCancel(
        spawnType: MobSpawnType,
        category: MobCategory,
        isCobblemon: Boolean,
        pos: BlockPos,
        areas: Iterable<WorkArea>,
    ): Boolean = spawnType == MobSpawnType.NATURAL &&
        category == MobCategory.MONSTER &&
        !isCobblemon &&
        areas.any { it.contains(pos) }
}
