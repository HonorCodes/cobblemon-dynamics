package codes.honor.cobblemondynamics.cell

import codes.honor.cobblemondynamics.registry.DynamicsBlocks
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentMap
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntity.DataComponentInput
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.capabilities.BlockCapabilityCache
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.energy.IEnergyStorage

/**
 * A Charge Cell's FE and its push into the blocks around it. NeoForge 21.9 replaces `IEnergyStorage` with its transfer API, so this
 * class and the capability registration are the only code of the cell that names it.
 */
class ChargeCellBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(DynamicsBlocks.CHARGE_CELL_TYPE.get(), pos, state) {
    private val store = CellStore()

    /** CD-GEN-08: the one store behind all six faces, which takes and gives FE on each. */
    val storage: IEnergyStorage = Storage()

    /** CD-GEN-10. */
    val signal: Int get() = ChargeCellRules.comparatorSignal(store.stored)

    /** The energy capability of the block on each side, in `Direction` order, made on the first server tick. */
    private var neighbours: List<BlockCapabilityCache<IEnergyStorage, Direction?>>? = null

    /** The face that gets the first FE that does not divide evenly. It moves on with each split, so no face always gets it. */
    private var turn = 0

    /**
     * CD-GEN-09: an equal split between the faces whose blocks take FE and are not Charge Cells, then the rest levels each
     * neighbouring cell that holds less. FE never flows into a cell that holds more, so it cannot loop between two cells.
     */
    fun serverTick(level: ServerLevel) {
        if (store.stored <= 0) return
        val caches = neighbours ?: Direction.entries.map { side ->
            BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, level, worldPosition.relative(side), side.opposite)
        }.also { neighbours = it }
        val machines = ArrayList<IEnergyStorage>(caches.size)
        val cells = ArrayList<Storage>(caches.size)
        for (cache in caches) {
            val target = cache.capability ?: continue
            if (target is Storage) {
                cells += target
            } else if (target.canReceive()) {
                machines += target
            }
        }
        if (machines.isNotEmpty()) {
            val limits = IntArray(machines.size) { machines[it].receiveEnergy(store.stored, true) }
            val shares = ChargeCellRules.split(store.stored, limits, turn++)
            for ((i, machine) in machines.withIndex()) give(machine, shares[i])
        }
        if (cells.isNotEmpty() && store.stored > 0) {
            val shares = ChargeCellRules.levelWith(store.stored, IntArray(cells.size) { cells[it].energyStored })
            for ((i, cell) in cells.withIndex()) give(cell, shares[i])
        }
    }

    private fun give(target: IEnergyStorage, fe: Int) {
        if (fe <= 0) return
        val taken = target.receiveEnergy(fe, false)
        if (taken > 0) storage.extractEnergy(minOf(taken, fe), false)
    }

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        tag.putInt(ENERGY, store.stored)
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        store.load(tag.getInt(ENERGY))
    }

    /** CD-GEN-11: the loot table copies this component to the drop of a broken cell, so the drop keeps the FE. */
    override fun collectImplicitComponents(components: DataComponentMap.Builder) {
        super.collectImplicitComponents(components)
        if (store.stored > 0) components.set(DynamicsBlocks.STORED_ENERGY.get(), store.stored)
    }

    /** CD-GEN-11: a cell placed from an item takes the item's FE. */
    override fun applyImplicitComponents(input: DataComponentInput) {
        super.applyImplicitComponents(input)
        store.load(input.getOrDefault(DynamicsBlocks.STORED_ENERGY.get(), 0))
    }

    override fun setRemoved() {
        super.setRemoved()
        neighbours = null
    }

    /** The capability object. A real change marks the block entity changed, which also tells comparators (CD-GEN-10). */
    inner class Storage : IEnergyStorage {
        override fun receiveEnergy(toReceive: Int, simulate: Boolean): Int = changed(store.receive(toReceive, simulate), simulate)

        override fun extractEnergy(toExtract: Int, simulate: Boolean): Int = changed(store.extract(toExtract, simulate), simulate)

        override fun getEnergyStored(): Int = store.stored

        override fun getMaxEnergyStored(): Int = store.capacity

        override fun canExtract(): Boolean = true

        override fun canReceive(): Boolean = true

        private fun changed(fe: Int, simulate: Boolean): Int {
            if (!simulate && fe > 0) this@ChargeCellBlockEntity.setChanged()
            return fe
        }
    }

    private companion object {
        const val ENERGY = "energy"
    }
}
