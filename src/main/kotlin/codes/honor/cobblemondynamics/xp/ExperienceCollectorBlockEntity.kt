package codes.honor.cobblemondynamics.xp

import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.registry.DynamicsBlocks
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentMap
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntity.DataComponentInput
import net.minecraft.world.level.block.state.BlockState

/** An Experience Collector's XP (CD-XPC-03), which the item of a broken collector keeps (CD-XPC-13). */
class ExperienceCollectorBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(DynamicsBlocks.EXPERIENCE_COLLECTOR_TYPE.get(), pos, state) {
    /** Up to `experienceCollector.capacity` of the server config at each change (CD-CFG-20). */
    val store = CollectorStore { WorkEngineSettings.current.experienceCollectorCapacity }

    /** CD-XPC-12. */
    val signal: Int get() = store.comparator()

    /** Adds up to [xp] and returns what the collector took. A change marks the block entity changed, which also tells comparators. */
    fun insert(xp: Int): Int = store.insert(xp).also { if (it > 0) setChanged() }

    /** Removes up to [xp], or only counts it when [simulate] is true, and returns what the collector gave. */
    fun extract(xp: Int, simulate: Boolean): Int = store.extract(xp, simulate).also { if (!simulate && it > 0) setChanged() }

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

    private companion object {
        const val EXPERIENCE = "experience"
    }
}
