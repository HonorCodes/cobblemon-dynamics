package codes.honor.cobblemondynamics.compat

import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.engine.WorkEngine
import codes.honor.cobblemondynamics.registry.DynamicsAttachments
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import java.util.UUID

/**
 * What the Cobblebase hooks in `mixin/compat/cobblebase` ask: whether Cobblebase leaves a pasture or a Pokémon alone. Cobblebase
 * 2.0.1 runs its pasture loop inside its own mixin handler, which another mixin cannot target, so the hooks sit in the helper
 * methods that the loop calls (CD-CMP-03). Pastures that are not in Work Mode keep Cobblebase as it is (CD-CMP-04). Only those
 * hooks load this class, and they apply only while Cobblebase is loaded (CD-CMP-07).
 */
object CobblebaseCompat {
    /** True for a pasture in Work Mode while `compat.cobblebase` is on. */
    @JvmStatic
    fun skipsPasture(level: Level, pos: BlockPos): Boolean {
        val serverLevel = level as? ServerLevel ?: return false
        if (!WorkEngineSettings.current.compatCobblebase) return false
        return serverLevel.getExistingDataOrNull(DynamicsAttachments.WORK_PASTURES)?.contains(pos) == true
    }

    /** True for a Pokémon that the work engine runs while `compat.cobblebase` is on. Server thread only. */
    @JvmStatic
    fun skipsPokemon(pokemonId: UUID): Boolean {
        val engine = WorkEngine.get() ?: return false
        if (!engine.server.isSameThread || !WorkEngineSettings.current.compatCobblebase) return false
        return engine.worker(pokemonId) != null
    }

    @JvmStatic
    fun skipsEntity(entity: PokemonEntity): Boolean = !entity.level().isClientSide && skipsPokemon(entity.pokemon.uuid)
}
