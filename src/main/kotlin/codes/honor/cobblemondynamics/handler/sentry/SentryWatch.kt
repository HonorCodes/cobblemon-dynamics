package codes.honor.cobblemondynamics.handler.sentry

import codes.honor.cobblemondynamics.engine.WorkArea
import codes.honor.cobblemondynamics.pasture.PastureKey
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.MobSpawnType
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent
import java.util.concurrent.ConcurrentHashMap

object SentryWatch {
    private class Entry(val area: WorkArea, val until: Long)

    private val watched = ConcurrentHashMap<ResourceLocation, ConcurrentHashMap<Long, Entry>>()

    fun register() {
        NeoForge.EVENT_BUS.addListener(::onFinalizeSpawn)
    }

    private fun onFinalizeSpawn(event: FinalizeSpawnEvent) {
        val level = event.level.level
        val dimension = level.dimension().location()
        val pos = BlockPos.containing(event.x, event.y, event.z)
        if (shouldCancel(dimension, pos, event.spawnType, event.entity.type.category, event.entity is PokemonEntity, level.gameTime)) {
            event.setSpawnCancelled(true)
        }
    }

    fun watch(pasture: PastureKey, area: WorkArea, now: Long) {
        watched.computeIfAbsent(pasture.dimension) { ConcurrentHashMap() }[pasture.pos] = Entry(area, now + 1L)
    }

    fun unwatch(pasture: PastureKey) {
        watched[pasture.dimension]?.remove(pasture.pos)
    }

    fun reset() {
        watched.clear()
    }

    fun shouldCancel(
        dimension: ResourceLocation,
        pos: BlockPos,
        spawnType: MobSpawnType,
        category: MobCategory,
        isCobblemon: Boolean,
        now: Long,
    ): Boolean {
        val byPasture = watched[dimension] ?: return false
        if (byPasture.isEmpty()) return false
        val activeAreas = ArrayList<WorkArea>(byPasture.size)
        for ((key, entry) in byPasture.entries) {
            if (now <= entry.until) {
                activeAreas.add(entry.area)
            } else {
                byPasture.remove(key, entry)
            }
        }
        if (activeAreas.isEmpty()) return false
        return SentryRules.shouldCancel(spawnType, category, isCobblemon, pos, activeAreas)
    }
}
