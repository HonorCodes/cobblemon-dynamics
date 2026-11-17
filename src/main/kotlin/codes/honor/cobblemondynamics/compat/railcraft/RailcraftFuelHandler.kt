package codes.honor.cobblemondynamics.compat.railcraft

import codes.honor.cobblemondynamics.compat.railcraft.RailcraftFuelRule.Kind
import codes.honor.cobblemondynamics.compat.railcraft.RailcraftFuelRule.Slot
import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import mods.railcraft.world.item.RailcraftItems
import mods.railcraft.world.item.crafting.RailcraftRecipeTypes
import mods.railcraft.world.level.block.entity.BlastFurnaceBlockEntity
import mods.railcraft.world.level.block.entity.steamboiler.SolidFueledSteamBoilerBlockEntity
import mods.railcraft.world.level.block.entity.steamboiler.SteamBoilerBlockEntity
import mods.railcraft.world.level.block.steamboiler.SolidFueledFireboxBlock
import mods.railcraft.world.level.material.steam.SteamBoiler
import mods.railcraft.world.module.BlastFurnaceModule
import mods.railcraft.world.module.SteamBoilerModule
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.SingleRecipeInput
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * The Stoker on Railcraft Reborn: its blast furnace from level 50, and each block of a solid-fueled firebox (CD-FUEL-04, CD-FUEL-05).
 * It writes the burn time through the public setters of the furnace's module and of the master's boiler, with no mixin.
 */
object RailcraftFuelHandler : TargetHandler {
    override val id: ResourceLocation = id("railcraft_fuel")
    override val scanKind: ScanKind = ScanKind.BLOCK_ENTITY

    /**
     * CD-FUEL-04: an idle worker claims a target only while it has demand for that worker, so a worker below the level of the blast
     * furnace never holds one that a stronger worker could fuel. A firebox has demand while its boiler holds water.
     */
    override val releasesWhenDone: Boolean = true

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        kindOf(blockEntity, state) != null && masterOf(blockEntity) != null

    override fun canonical(level: ServerLevel, pos: BlockPos): BlockPos {
        val blockEntity = level.getBlockEntity(pos)
        val kind = kindOf(blockEntity, level.getBlockState(pos)) ?: return pos
        return RailcraftFuelRule.canonical(kind, pos, masterOf(blockEntity)?.blockPos)
    }

    override fun isPartOfTarget(level: ServerLevel, canonical: BlockPos, pos: BlockPos): Boolean {
        if (pos == canonical) return true
        val blockEntity = level.getBlockEntity(pos) as? BlastFurnaceBlockEntity ?: return false
        return masterOf(blockEntity)?.blockPos == canonical
    }

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean {
        val blockEntity = level.getBlockEntity(canonical)
        return when (kindOf(blockEntity, level.getBlockState(canonical))) {
            Kind.BLAST_FURNACE -> masterOf(blockEntity)?.blockPos == canonical
            Kind.FIREBOX -> masterOf(blockEntity) != null
            null -> false
        }
    }

    override fun hasDemand(ctx: WorkContext): Boolean {
        val blockEntity = ctx.level.getBlockEntity(ctx.target)
        val kind = kindOf(blockEntity, ctx.level.getBlockState(ctx.target)) ?: return false
        val params = RailcraftFuelParams.of(ctx.role.params)
        if (!RailcraftFuelRule.admits(kind, ctx.pokemon.level, params.blastFurnaceMinLevel)) return false
        return when (kind) {
            Kind.BLAST_FURNACE -> furnaceModule(blockEntity)?.let { recipeFits(ctx.level, it) } ?: false
            Kind.FIREBOX -> boilerOf(blockEntity)?.let { RailcraftFuelRule.fireboxDemand(true, it.getWaterTank().fluidAmount > 0) } ?: false
        }
    }

    override fun applyWork(ctx: WorkContext) {
        if (!hasDemand(ctx)) return
        val blockEntity = ctx.level.getBlockEntity(ctx.target)
        when (kindOf(blockEntity, ctx.level.getBlockState(ctx.target))) {
            Kind.BLAST_FURNACE -> furnaceModule(blockEntity)?.let(::fuelFurnace)
            Kind.FIREBOX -> fuelBoiler(blockEntity, RailcraftFuelParams.of(ctx.role.params).fireboxBurnTicksPerTick)
            null -> {}
        }
    }

    /** CD-FUEL-04: the furnace's master tick sets its lit state from the burn time. The gauge reads full. */
    private fun fuelFurnace(module: BlastFurnaceModule) {
        val burn = RailcraftFuelRule.furnaceBurn(module.getBurnTime())
        if (burn == module.getBurnTime()) return
        module.setBurnTime(burn)
        module.setCurrentItemBurnTime(burn)
    }

    /** CD-FUEL-05: adds this firebox block's burn ticks to the master's boiler, up to what its next cycle burns. */
    private fun fuelBoiler(blockEntity: BlockEntity?, cap: Double) {
        val master = masterOf(blockEntity) as? SteamBoilerBlockEntity ?: return
        val boiler = boilerOf(blockEntity) ?: return
        val tanks = master.getCurrentPattern().orElse(null)?.getMetadata()?.tanks() ?: 0
        if (tanks <= 0) return
        val gift = RailcraftFuelRule.fireboxGift(boiler.getBurnTime(), boiler.getFuelPerCycle(tanks), cap)
        if (gift > 0f) boiler.setBurnTime(boiler.getBurnTime() + gift)
    }

    private fun kindOf(blockEntity: BlockEntity?, state: BlockState): Kind? = when {
        blockEntity is BlastFurnaceBlockEntity -> Kind.BLAST_FURNACE
        blockEntity is SolidFueledSteamBoilerBlockEntity && state.block is SolidFueledFireboxBlock -> Kind.FIREBOX
        else -> null
    }

    /** The master of the multiblock that holds [blockEntity], or null while the multiblock is not formed. */
    private fun masterOf(blockEntity: BlockEntity?): BlockEntity? = when (blockEntity) {
        is BlastFurnaceBlockEntity -> blockEntity.getMasterBlockEntity().orElse(null)
        is SteamBoilerBlockEntity -> blockEntity.getMasterBlockEntity().orElse(null)
        else -> null
    }

    private fun furnaceModule(blockEntity: BlockEntity?): BlastFurnaceModule? =
        (masterOf(blockEntity) as? BlastFurnaceBlockEntity)?.getBlastFurnaceModule()

    /** The boiler of the master, which ticks it. The other blocks of a boiler hold modules of their own that never burn. */
    private fun boilerOf(blockEntity: BlockEntity?): SteamBoiler? {
        val master = masterOf(blockEntity) as? SteamBoilerBlockEntity ?: return null
        return master.getModule(SteamBoilerModule::class.java).map { it.getBoiler() }.orElse(null)
    }

    /** Railcraft's own requirement, a blasting recipe for the input, and room for the result and the slag. */
    private fun recipeFits(level: ServerLevel, module: BlastFurnaceModule): Boolean {
        val input = module.getItem(BlastFurnaceModule.SLOT_INPUT)
        if (input.isEmpty) return false
        val recipe = level.recipeManager.getRecipeFor(RailcraftRecipeTypes.BLASTING.get(), SingleRecipeInput(input), level)
            .map { it.value() }.orElse(null) ?: return false
        val result = recipe.getResultItem(level.registryAccess())
        val slag = ItemStack(RailcraftItems.SLAG.get(), recipe.slagOutput)
        val slots = listOf(BlastFurnaceModule.SLOT_OUTPUT to result, BlastFurnaceModule.SLOT_SLAG to slag)
            .filter { (_, adding) -> !adding.isEmpty }
            .map { (slot, adding) ->
                val held = module.getItem(slot)
                Slot(held.isEmpty, ItemStack.isSameItemSameComponents(held, adding), held.count, adding.count, held.maxStackSize)
            }
        return RailcraftFuelRule.fits(slots)
    }
}
