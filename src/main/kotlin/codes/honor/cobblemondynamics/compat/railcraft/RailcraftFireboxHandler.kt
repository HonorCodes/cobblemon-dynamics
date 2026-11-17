package codes.honor.cobblemondynamics.compat.railcraft

import codes.honor.cobblemondynamics.claim.TargetKey
import codes.honor.cobblemondynamics.engine.WorkEngine
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.handler.ScanKind
import codes.honor.cobblemondynamics.handler.TargetHandler
import codes.honor.cobblemondynamics.handler.WorkContext
import codes.honor.cobblemondynamics.id
import mods.railcraft.world.level.block.entity.multiblock.MultiblockPattern
import mods.railcraft.world.level.block.entity.steamboiler.SolidFueledSteamBoilerBlockEntity
import mods.railcraft.world.level.block.entity.steamboiler.SteamBoilerBlockEntity
import mods.railcraft.world.level.block.steamboiler.SolidFueledFireboxBlock
import mods.railcraft.world.level.material.steam.SteamBoiler
import mods.railcraft.world.module.SteamBoilerModule
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * The Stoker on the solid-fueled boilers of Railcraft Reborn, through the Work Leash only (CD-FUEL-05). Each block of a firebox is its
 * own target with its own bound worker, and the workers of one boiler pool their supply: while the worker of every firebox block of
 * the boiler Works, they keep the master's burn time at the need of a fuel step at the boiler's top temperature, through its public
 * setters, with no mixin. A boiler below half of its water gets nothing (CD-FUEL-15).
 */
object RailcraftFireboxHandler : TargetHandler {
    /** A pattern cell of a firebox block, in `SteamBoilerBlockEntity.buildPattern`. */
    private const val FIREBOX = 'F'

    override val id: ResourceLocation = id("railcraft_firebox")

    /** Leash only: a scan never offers a firebox block, so a worker heats only a boiler that the player chose (R85). */
    override val scanKind: ScanKind = ScanKind.NONE

    override fun matches(level: ServerLevel, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Boolean =
        isFirebox(blockEntity, state) && masterOf(blockEntity) != null

    /**
     * Every block of the boiler counts as part of each firebox target, so that a worker beside the boiler sees an inner firebox
     * block, which the other blocks of the boiler surround, through them.
     */
    override fun isPartOfTarget(level: ServerLevel, canonical: BlockPos, pos: BlockPos): Boolean {
        if (pos == canonical) return true
        val master = masterOf(level.getBlockEntity(canonical)) ?: return false
        return masterOf(level.getBlockEntity(pos)) === master
    }

    override fun stillValid(level: ServerLevel, canonical: BlockPos): Boolean {
        val blockEntity = level.getBlockEntity(canonical)
        return isFirebox(blockEntity, level.getBlockState(canonical)) && masterOf(blockEntity) != null
    }

    /** A binding keeps its firebox block while the boiler forms again, as Railcraft does some ticks after a chunk loads (CD-LSH-08). */
    override fun bindingHolds(level: ServerLevel, canonical: BlockPos): Boolean =
        isFirebox(level.getBlockEntity(canonical), level.getBlockState(canonical))

    override fun hasDemand(ctx: WorkContext): Boolean = boilerAt(ctx)?.let { RailcraftBoilerRule.hasDemand(it.pool) } ?: false

    /**
     * CD-FUEL-05, CD-FUEL-13: the first worker of a full pool on a tick tops the boiler up for all of them, so the pool reaches the
     * need on the tick that it fills, wherever the boiler is in its cycle. Each fuel step burns its need, and the pool gives it back.
     * The gauge reads full.
     */
    override fun applyWork(ctx: WorkContext) {
        val boiler = boilerAt(ctx) ?: return
        if (!RailcraftBoilerRule.supplies(boiler.pool)) return
        val steam = boiler.steam
        val burn = RailcraftBoilerRule.poolBurn(
            steam.getFuelPerCycle(boiler.tanks),
            boiler.tanks,
            steam.temperaturePercent,
            steam.maxTemperature,
        )
        if (steam.getBurnTime() >= burn) return
        steam.setBurnTime(burn)
        steam.setCurrentItemBurnTime(burn)
    }

    /** The master's boiler, its tank count and its pool, read for one tick. */
    private class Boiler(val steam: SteamBoiler, val tanks: Int, val pool: RailcraftBoilerRule.Pool)

    private fun boilerAt(ctx: WorkContext): Boiler? {
        val blockEntity = ctx.level.getBlockEntity(ctx.target)
        if (!isFirebox(blockEntity, ctx.level.getBlockState(ctx.target))) return null
        val master = masterOf(blockEntity) ?: return null
        val pattern = master.getCurrentPattern().orElse(null) ?: return null
        val tanks = pattern.getMetadata()?.tanks()?.takeIf { it > 0 } ?: return null
        val steam = master.getModule(SteamBoilerModule::class.java).map { it.getBoiler() }.orElse(null) ?: return null
        val fireboxes = fireboxesOf(ctx, master, pattern)
        val engine = WorkEngine.get()
        var held = 0
        var working = 0
        for (pos in fireboxes.positions) {
            val status = statusAt(engine, ctx, pos) ?: continue
            held++
            if (status == WorkerStatus.WORKING) working++
        }
        val water = steam.getWaterTank()
        return Boiler(steam, tanks, RailcraftBoilerRule.Pool(fireboxes.cells, held, working, water.fluidAmount, water.capacity))
    }

    /**
     * The status of the worker of this role whose work claim holds the firebox block at [pos], or null when none does. Only a bound
     * worker can claim a block of a leash-only role, and an unloaded block holds no claim.
     */
    private fun statusAt(engine: WorkEngine?, ctx: WorkContext, pos: BlockPos): WorkerStatus? {
        if (engine == null) return null
        val claim = engine.claims.holderOf(TargetKey.block(ctx.level.dimension().location(), pos)) ?: return null
        if (engine.roleOf(claim.worker)?.definition?.id != ctx.role.id) return null
        return engine.worker(claim.worker)?.status
    }

    /**
     * A claim's firebox blocks: the pattern's firebox cells, and the positions of the loaded firebox blocks that the master holds. A
     * boiler with a firebox block in an unloaded chunk lacks a position, so its pool never fills.
     */
    private class Fireboxes(val master: BlockEntity, val pattern: MultiblockPattern<*>, val cells: Int, val positions: List<BlockPos>)

    private fun fireboxesOf(ctx: WorkContext, master: SteamBoilerBlockEntity, pattern: MultiblockPattern<*>): Fireboxes {
        (ctx.memo.value as? Fireboxes)
            ?.takeIf { it.master === master && it.pattern === pattern && it.positions.size == it.cells }
            ?.let { return it }
        var cells = 0
        for (x in 0 until pattern.xSize) {
            for (y in 0 until pattern.ySize) {
                for (z in 0 until pattern.zSize) if (pattern.getMarker(BlockPos(x, y, z)) == FIREBOX) cells++
            }
        }
        val positions = master.getMembers().orElse(null).orEmpty()
            .filter { isFirebox(it, it.blockState) }
            .map { it.blockPos }
        return Fireboxes(master, pattern, cells, positions).also { ctx.memo.value = it }
    }

    private fun isFirebox(blockEntity: BlockEntity?, state: BlockState): Boolean =
        blockEntity is SolidFueledSteamBoilerBlockEntity && state.block is SolidFueledFireboxBlock

    /** The master of the boiler that holds [blockEntity], or null while the boiler is not formed. */
    private fun masterOf(blockEntity: BlockEntity?): SteamBoilerBlockEntity? =
        (blockEntity as? SteamBoilerBlockEntity)?.getMasterBlockEntity()?.orElse(null)
}
