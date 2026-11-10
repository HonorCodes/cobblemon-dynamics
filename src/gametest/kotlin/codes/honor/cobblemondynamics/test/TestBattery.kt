package codes.honor.cobblemondynamics.test

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.Registries
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.capabilities.ICapabilityProvider
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent
import net.neoforged.neoforge.energy.IEnergyStorage
import net.neoforged.neoforge.registries.DeferredBlock
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

/**
 * The test battery: a block of the full test mod with an FE store, exposed through NeoForge's energy capability. Only the full run
 * registers it, so it stays out of `kotlin-shared`.
 */
object TestBattery {
    private val BLOCKS: DeferredRegister.Blocks = DeferredRegister.createBlocks(TEST_MOD_ID)
    private val BLOCK_ENTITIES: DeferredRegister<BlockEntityType<*>> = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TEST_MOD_ID)

    val BLOCK: DeferredBlock<TestBatteryBlock> = BLOCKS.registerBlock("test_battery", ::TestBatteryBlock, BlockBehaviour.Properties.of())

    val TYPE: DeferredHolder<BlockEntityType<*>, BlockEntityType<TestBatteryBlockEntity>> = BLOCK_ENTITIES.register(
        "test_battery",
        Supplier { BlockEntityType.Builder.of(::TestBatteryBlockEntity, BLOCK.get()).build(null) },
    )

    fun register(modBus: IEventBus) {
        BLOCKS.register(modBus)
        BLOCK_ENTITIES.register(modBus)
        modBus.addListener<RegisterCapabilitiesEvent> { event ->
            event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                TYPE.get(),
                ICapabilityProvider<TestBatteryBlockEntity, Direction?, IEnergyStorage> { battery, side -> battery.storageFor(side) },
            )
        }
    }
}

/** A battery at [rel] that holds up to [capacity] FE and takes it on every face but those in [refuse]. */
fun TestKit.testBattery(
    helper: GameTestHelper,
    rel: BlockPos,
    capacity: Int = Int.MAX_VALUE,
    refuse: Set<Direction> = emptySet(),
): TestBatteryBlockEntity {
    helper.setBlock(rel, TestBattery.BLOCK.get())
    val battery = helper.getBlockEntity<TestBatteryBlockEntity>(rel)
    battery.capacity = capacity
    battery.refuse = refuse
    return battery
}

class TestBatteryBlock(properties: BlockBehaviour.Properties) : BaseEntityBlock(properties) {
    override fun codec(): MapCodec<TestBatteryBlock> = CODEC

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = TestBatteryBlockEntity(pos, state)

    companion object {
        val CODEC: MapCodec<TestBatteryBlock> = BlockBehaviour.simpleCodec(::TestBatteryBlock)
    }
}

/** Records every real insert. On a face in [refuse], its capability cannot receive, as a machine's face with no power input. */
class TestBatteryBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(TestBattery.TYPE.get(), pos, state) {
    /** One real insert: the tick, what the caller offered and what the battery took. */
    data class Offer(val tick: Long, val offered: Int, val accepted: Int)

    var capacity: Int = Int.MAX_VALUE
    var refuse: Set<Direction> = emptySet()

    var stored: Int = 0
        private set

    val offers = ArrayList<Offer>()

    /** Empties the store, as a machine that uses its energy does. */
    fun drain() {
        stored = 0
    }

    fun storageFor(side: Direction?): IEnergyStorage = if (side != null && side in refuse) refusing else receiving

    private val receiving = object : IEnergyStorage {
        override fun receiveEnergy(toReceive: Int, simulate: Boolean): Int {
            if (toReceive <= 0) return 0
            val accepted = minOf(toReceive, capacity - stored)
            if (!simulate) {
                stored += accepted
                offers += Offer(level?.gameTime ?: -1, toReceive, accepted)
            }
            return accepted
        }

        override fun extractEnergy(toExtract: Int, simulate: Boolean): Int = 0

        override fun getEnergyStored(): Int = stored

        override fun getMaxEnergyStored(): Int = capacity

        override fun canExtract(): Boolean = false

        override fun canReceive(): Boolean = true
    }

    private val refusing = object : IEnergyStorage {
        override fun receiveEnergy(toReceive: Int, simulate: Boolean): Int = 0

        override fun extractEnergy(toExtract: Int, simulate: Boolean): Int = 0

        override fun getEnergyStored(): Int = stored

        override fun getMaxEnergyStored(): Int = capacity

        override fun canExtract(): Boolean = false

        override fun canReceive(): Boolean = false
    }
}
