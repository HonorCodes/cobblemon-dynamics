package codes.honor.cobblemondynamics.handler.create

import codes.honor.cobblemondynamics.CobblemonDynamics
import codes.honor.cobblemondynamics.MixinState
import codes.honor.cobblemondynamics.handler.HandlerRegistry
import com.simibubi.create.AllBlockEntityTypes
import com.simibubi.create.api.event.BlockEntityBehaviourEvent
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity
import com.simibubi.create.foundation.block.ItemUseOverrides
import net.neoforged.neoforge.common.NeoForge

/**
 * The Create integration. Every class that names a Create type sits in this package or in `mixin.create`, and only [register]
 * is called from outside, behind `ModList.isLoaded("create")`, so no Create class loads without Create (CD-PLAT-05).
 */
object CreateCompat {
    fun register() {
        // A mixin applies when its target class loads. These class literals load each target of the `create.` mixins during mod
        // construction, so MixinState holds its final answer before the first role build reads it (CD-WHL-10, CD-BRN-07).
        val mixinTargets = listOf(WaterWheelBlockEntity::class.java, BlazeBurnerBlockEntity::class.java, ItemUseOverrides::class.java)
        CobblemonDynamics.LOGGER.debug("Create integration on, mixin targets loaded: {}", mixinTargets.map { it.simpleName })
        if (!MixinState.applied(MixinState.ITEM_USE_OVERRIDES)) {
            CobblemonDynamics.LOGGER.warn("The Work Leash cannot bind or flip Create hand cranks: its crank mixin did not apply")
        }
        NeoForge.EVENT_BUS.addListener(BlockEntityBehaviourEvent::class.java, ::attachDrive)
        HandlerRegistry.register(WaterWheelHandler)
        HandlerRegistry.register(HandCrankHandler)
        HandlerRegistry.register(BurnerHeatHandler)
    }

    /**
     * Create posts the event on both sides, on a block entity's first tick or first load. A valve handle has its own block entity
     * type, so it gets no drive.
     */
    private fun attachDrive(event: BlockEntityBehaviourEvent) {
        event.forType(AllBlockEntityTypes.WATER_WHEEL.get()) { event.attach(DriveBehaviour(it)) }
        event.forType(AllBlockEntityTypes.LARGE_WATER_WHEEL.get()) { event.attach(DriveBehaviour(it)) }
        event.forType(AllBlockEntityTypes.HAND_CRANK.get()) { event.attach(DriveBehaviour(it)) }
    }
}
