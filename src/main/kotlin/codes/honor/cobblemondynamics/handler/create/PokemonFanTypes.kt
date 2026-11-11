package codes.honor.cobblemondynamics.handler.create

import codes.honor.cobblemondynamics.MOD_ID
import com.simibubi.create.api.registry.CreateRegistries
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock
import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

/**
 * Create's fan processing for a Pokémon catalyst. It is valid only in the block in front of a fan whose [FanState] holds a catalyst of
 * [kind] (and, for Fire, [mode]). Everything else is Create's own type, so items process as Create's bulk processing does, at Create's
 * speed (CD-FAN-05 to CD-FAN-08, CD-BAL-04). It runs on both sides, so it names no client class.
 */
class PokemonFanType(private val kind: FanCatalyst, private val mode: FireMode?, private val create: FanProcessingType) :
    FanProcessingType {
    override fun isValidAt(level: Level, pos: BlockPos): Boolean = Direction.entries.any { side ->
        val at = pos.relative(side)
        // Create asks for every block of every air current, so a neighbour in an unloaded chunk is never loaded for it.
        if (!level.isLoaded(at)) return@any false
        val fan = level.getBlockEntity(at) as? EncasedFanBlockEntity ?: return@any false
        val facing = fan.blockState.getOptionalValue(DirectionalKineticBlock.FACING).orElse(null)
        facing == side.opposite && FanState.of(fan)?.catalyses(kind, mode) == true
    }

    /** CD-FAN-13: above Create's splashing at 400, so a worker that stands in water is still its own catalyst. */
    override fun getPriority(): Int = PokemonFanTypes.PRIORITY

    override fun canProcess(stack: ItemStack, level: Level): Boolean = create.canProcess(stack, level)

    override fun process(stack: ItemStack, level: Level): List<ItemStack>? = create.process(stack, level)

    override fun spawnProcessingParticles(level: Level, pos: Vec3) = create.spawnProcessingParticles(level, pos)

    override fun morphAirFlow(particle: FanProcessingType.AirFlowParticleAccess, random: RandomSource) =
        create.morphAirFlow(particle, random)

    override fun affectEntity(entity: Entity, level: Level) = create.affectEntity(entity, level)
}

/** The registry entries of [PokemonFanType]. The registry is synced, and the mod is on both sides (CD-PLAT-06). */
object PokemonFanTypes {
    /** Above Create's built-in types: splashing 400, haunting 300, smoking 200 and blasting 100. */
    const val PRIORITY = 500

    val REGISTER: DeferredRegister<FanProcessingType> = DeferredRegister.create(CreateRegistries.FAN_PROCESSING_TYPE, MOD_ID)

    val BLASTING: DeferredHolder<FanProcessingType, PokemonFanType> = REGISTER.register(
        "pokemon_blasting",
        Supplier { PokemonFanType(FanCatalyst.FIRE, FireMode.BLASTING, AllFanProcessingTypes.BLASTING) },
    )

    val SMOKING: DeferredHolder<FanProcessingType, PokemonFanType> = REGISTER.register(
        "pokemon_smoking",
        Supplier { PokemonFanType(FanCatalyst.FIRE, FireMode.SMOKING, AllFanProcessingTypes.SMOKING) },
    )

    val SPLASHING: DeferredHolder<FanProcessingType, PokemonFanType> = REGISTER.register(
        "pokemon_splashing",
        Supplier { PokemonFanType(FanCatalyst.WATER, null, AllFanProcessingTypes.SPLASHING) },
    )

    val HAUNTING: DeferredHolder<FanProcessingType, PokemonFanType> = REGISTER.register(
        "pokemon_haunting",
        Supplier { PokemonFanType(FanCatalyst.GHOST, null, AllFanProcessingTypes.HAUNTING) },
    )
}
