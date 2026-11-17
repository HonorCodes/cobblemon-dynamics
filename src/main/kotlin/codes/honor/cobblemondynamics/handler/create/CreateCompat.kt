package codes.honor.cobblemondynamics.handler.create

import codes.honor.cobblemondynamics.CobblemonDynamics
import codes.honor.cobblemondynamics.MixinState
import codes.honor.cobblemondynamics.OnceLogger
import codes.honor.cobblemondynamics.compat.create_connected.CreateConnectedCompat
import codes.honor.cobblemondynamics.handler.HandlerRegistry
import com.simibubi.create.AllBlockEntityTypes
import com.simibubi.create.api.event.BlockEntityBehaviourEvent
import com.simibubi.create.content.contraptions.bearing.WindmillBearingBlockEntity
import com.simibubi.create.content.kinetics.fan.AirCurrent
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity
import com.simibubi.create.foundation.block.ItemUseOverrides
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModList
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.server.ServerStoppedEvent

/**
 * The Create integration. Every class that names a Create type sits in this package or in `mixin.create`, or in the compat package
 * of an add-on of Create, which loads only with that add-on and so with Create. Only [register] is called from outside, behind
 * `ModList.isLoaded("create")`, so no Create class loads without Create (CD-PLAT-05).
 */
object CreateCompat {
    private val warnings = OnceLogger()

    /** Create: Connected needs Create, so its hook runs here, behind its own mod id. */
    private val createConnected = ModList.get().isLoaded(CreateConnectedCompat.MOD_ID)

    fun register(modBus: IEventBus) {
        // A mixin applies when its target class loads. These class literals load each target of the `create.` mixins during mod
        // construction, so MixinState holds its final answer before the first role build reads it (CD-WHL-10, CD-BRN-07, CD-FAN-17,
        // CD-TLW-10).
        val mixinTargets = listOf(
            WaterWheelBlockEntity::class.java,
            BlazeBurnerBlockEntity::class.java,
            ItemUseOverrides::class.java,
            AirCurrent::class.java,
            WindmillBearingBlockEntity::class.java,
        )
        CobblemonDynamics.LOGGER.debug("Create integration on, mixin targets loaded: {}", mixinTargets.map { it.simpleName })
        PokemonFanTypes.REGISTER.register(modBus)
        if (!MixinState.applied(MixinState.ITEM_USE_OVERRIDES)) {
            warnings.warn(MixinState.ITEM_USE_OVERRIDES) {
                "The Work Leash cannot bind or flip Create hand cranks: its crank mixin did not apply"
            }
        }
        NeoForge.EVENT_BUS.addListener(BlockEntityBehaviourEvent::class.java, ::attachBehaviours)
        NeoForge.EVENT_BUS.addListener<ServerStoppedEvent> { PokemonHeat.reset() }
        HandlerRegistry.register(WaterWheelHandler)
        HandlerRegistry.register(HandCrankHandler)
        HandlerRegistry.register(BurnerHeatHandler)
        HandlerRegistry.register(FanCatalystHandler)
        HandlerRegistry.register(TailwindHandler)
    }

    /**
     * Create posts the event on both sides, on a block entity's first tick or first load. A valve handle has its own block entity
     * type, so it gets no drive.
     */
    private fun attachBehaviours(event: BlockEntityBehaviourEvent) {
        event.forType(AllBlockEntityTypes.WATER_WHEEL.get()) { event.attach(DriveBehaviour(it)) }
        event.forType(AllBlockEntityTypes.LARGE_WATER_WHEEL.get()) { event.attach(DriveBehaviour(it)) }
        event.forType(AllBlockEntityTypes.HAND_CRANK.get()) { event.attach(DriveBehaviour(it)) }
        // CD-KIN-05: Create: Connected's crank wheels are hand cranks to Create, on a block entity type of their own.
        if (createConnected) {
            @Suppress("UNCHECKED_CAST")
            val wheels = CreateConnectedCompat.crankWheelType() as BlockEntityType<SmartBlockEntity>?
            if (wheels != null) event.forType(wheels) { event.attach(DriveBehaviour(it)) }
        }
        event.forType(AllBlockEntityTypes.ENCASED_FAN.get()) { event.attach(FanState(it)) }
        event.forType(AllBlockEntityTypes.WINDMILL_BEARING.get()) { event.attach(TailwindBonus(it)) }
    }
}
