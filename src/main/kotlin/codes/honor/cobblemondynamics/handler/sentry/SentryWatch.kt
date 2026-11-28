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
    private val watched = ConcurrentHashMap<ResourceLocation, ConcurrentHashMap<Long, WorkArea>>()

    fun register() {
        NeoForge.EVENT_BUS.addListener(::onFinalizeSpawn)
    }

    private fun onFinalizeSpawn(event: FinalizeSpawnEvent) {
        val dimension = event.level.level.dimension().location()
        val pos = BlockPos.containing(event.x, event.y, event.z)
        if (shouldCancel(dimension, pos, event.spawnType, event.entity.type.category, event.entity is PokemonEntity)) {
            event.setSpawnCancelled(true)
        }
    }

    fun watch(pasture: PastureKey, area: WorkArea) {
        watched.computeIfAbsent(pasture.dimension) { ConcurrentHashMap() }[pasture.pos] = area
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
    ): Boolean {
        val byPasture = watched[dimension] ?: return false
        if (byPasture.isEmpty()) return false
        return SentryRules.shouldCancel(spawnType, category, isCobblemon, pos, byPasture.values)
    }
}
