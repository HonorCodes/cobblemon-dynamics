package codes.honor.cobblemondynamics.xp

import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.registry.DynamicsBlocks
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentMap
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntity.DataComponentInput
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import java.util.Collections
import java.util.WeakHashMap

/**
 * An Experience Collector's XP (CD-XPC-03), which the item of a broken collector keeps (CD-XPC-13), and the tank that its back and
 * its bottom offer to pipes (CD-XPC-07).
 */
class ExperienceCollectorBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(DynamicsBlocks.EXPERIENCE_COLLECTOR_TYPE.get(), pos, state) {
    /** Up to `experienceCollector.capacity` of the server config at each change (CD-CFG-20). */
    val store = CollectorStore { WorkEngineSettings.current.experienceCollectorCapacity }

    /** CD-XPC-07: the one tank behind both outputs, of the fluid of the last tag load (CD-XPC-08). */
    val fluidTank: IFluidHandler = XpFluidTank(XpTank(store) { XpFluids.current }, ::setChanged)

    /** CD-XPC-12. */
    val signal: Int get() = store.comparator()

    /** The way that the front faces. */
    val front: Direction get() = blockState.getValue(ExperienceCollectorBlock.FACING)

    /** CD-XPC-07: true for the back face and the bottom face only. */
    fun isOutput(side: Direction?): Boolean = CollectorFaces.isOutput(front, side)

    /** Adds up to [xp] and returns what the collector took. A change marks the block entity changed, which also tells comparators. */
    fun insert(xp: Int): Int = store.insert(xp).also { if (it > 0) setChanged() }

    /** Removes up to [xp], or only counts it when [simulate] is true, and returns what the collector gave. */
    fun extract(xp: Int, simulate: Boolean): Int = store.extract(xp, simulate).also { if (!simulate && it > 0) setChanged() }

    /** A turned collector has its outputs on other faces, so its neighbours look up its capabilities again. */
    @Deprecated("Deprecated in Java")
    override fun setBlockState(state: BlockState) {
        val before = front
        @Suppress("DEPRECATION")
        super.setBlockState(state)
        if (front != before) invalidateCapabilities()
    }

    override fun onLoad() {
        super.onLoad()
        if (level?.isClientSide == false) synchronized(LOADED) { LOADED += this }
    }

    override fun setRemoved() {
        super.setRemoved()
        synchronized(LOADED) { LOADED -= this }
    }

    override fun onChunkUnloaded() {
        super.onChunkUnloaded()
        synchronized(LOADED) { LOADED -= this }
    }

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        tag.putInt(EXPERIENCE, store.stored)
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        store.load(tag.getInt(EXPERIENCE))
    }

    /** CD-XPC-13: the loot table copies this component to the drop of a broken collector, so the drop keeps the XP. */
    override fun collectImplicitComponents(components: DataComponentMap.Builder) {
        super.collectImplicitComponents(components)
        if (store.stored > 0) components.set(DynamicsBlocks.STORED_EXPERIENCE.get(), store.stored)
    }

    /** CD-XPC-13: a collector placed from an item takes the item's XP. */
    override fun applyImplicitComponents(input: DataComponentInput) {
        super.applyImplicitComponents(input)
        store.load(input.getOrDefault(DynamicsBlocks.STORED_EXPERIENCE.get(), 0))
    }

    companion object {
        private const val EXPERIENCE = "experience"

        /** The collectors that a server level holds now. */
        private val LOADED: MutableSet<ExperienceCollectorBlockEntity> = Collections.newSetFromMap(WeakHashMap())

        /** CD-XPC-08: the outputs gained or lost their fluid, so every loaded collector tells its neighbours. */
        fun invalidateLoaded() {
            val loaded = synchronized(LOADED) { LOADED.toList() }
            for (collector in loaded) if (!collector.isRemoved) collector.invalidateCapabilities()
        }
    }
}
