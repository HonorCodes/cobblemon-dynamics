package codes.honor.cobblemondynamics.compat.jade

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.bed.BedBlock
import codes.honor.cobblemondynamics.config.WorkEngineSettings
import codes.honor.cobblemondynamics.engine.WorkEngine
import codes.honor.cobblemondynamics.engine.WorkerStatus
import codes.honor.cobblemondynamics.id
import codes.honor.cobblemondynamics.pasture.PastureAdapter
import codes.honor.cobblemondynamics.registry.DynamicsAttachments
import codes.honor.cobblemondynamics.stamina.RecoveryRule
import com.cobblemon.mod.common.block.PastureBlock
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import snownee.jade.api.BlockAccessor
import snownee.jade.api.EntityAccessor
import snownee.jade.api.IBlockComponentProvider
import snownee.jade.api.IEntityComponentProvider
import snownee.jade.api.IServerDataProvider
import snownee.jade.api.ITooltip
import snownee.jade.api.IWailaClientRegistration
import snownee.jade.api.IWailaCommonRegistration
import snownee.jade.api.IWailaPlugin
import snownee.jade.api.WailaPlugin
import snownee.jade.api.config.IPluginConfig

@WailaPlugin(MOD_ID)
class DynamicsJadePlugin : IWailaPlugin {
    override fun register(registration: IWailaCommonRegistration) {
        registration.registerEntityDataProvider(WorkerJadeProvider, PokemonEntity::class.java)
        registration.registerBlockDataProvider(BlockJadeProvider, Block::class.java)
    }

    override fun registerClient(registration: IWailaClientRegistration) {
        registration.registerEntityComponent(WorkerJadeProvider, PokemonEntity::class.java)
        registration.registerBlockComponent(BlockJadeProvider, Block::class.java)
    }
}

private object WorkerJadeProvider : IEntityComponentProvider, IServerDataProvider<EntityAccessor> {
    private val UID: ResourceLocation = id("jade_worker")
    private const val TAG_ROLE = "cd_role"
    private const val TAG_MULTIPLIER = "cd_mult"

    override fun getUid(): ResourceLocation = UID

    override fun appendServerData(data: CompoundTag, accessor: EntityAccessor) {
        val entity = accessor.entity as? PokemonEntity ?: return
        val engine = WorkEngine.get() ?: return
        val pokemonId = entity.pokemon.uuid
        if (engine.worker(pokemonId) == null) return
        val rolePath = engine.roleOf(pokemonId)?.definition?.id?.path
            ?: "healer".takeIf { engine.ruleOf(pokemonId) == RecoveryRule.HEALER }
        if (rolePath != null) data.putString(TAG_ROLE, rolePath)
        data.putDouble(TAG_MULTIPLIER, engine.multiplierOf(pokemonId))
    }

    override fun appendTooltip(tooltip: ITooltip, accessor: EntityAccessor, config: IPluginConfig) {
        val entity = accessor.entity as? PokemonEntity ?: return
        val view = entity.getExistingDataOrNull(DynamicsAttachments.STAMINA_VIEW) ?: return
        val serverData = accessor.serverData
        val rolePath = serverData.getString(TAG_ROLE).takeIf { it.isNotEmpty() }
        val multiplier = if (serverData.contains(TAG_MULTIPLIER, Tag.TAG_DOUBLE.toInt())) {
            serverData.getDouble(TAG_MULTIPLIER)
        } else {
            1.0
        }
        val lines = JadeTooltipRules.workerLines(
            percent = view.percent,
            status = view.state,
            idleReason = view.idleReason,
            traitFlags = view.traitFlags,
            rolePath = rolePath,
            multiplier = multiplier,
        )
        for (spec in lines) {
            tooltip.add(spec.toComponent())
        }
    }
}

private object BlockJadeProvider : IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    private val UID: ResourceLocation = id("jade_block")
    private const val TAG_PASTURE_PRESENT = "cd_pasture"
    private const val TAG_PASTURE_MODE = "cd_work_mode"
    private const val TAG_PASTURE_RADIUS = "cd_radius"
    private const val TAG_PASTURE_LOCKED = "cd_locked"
    private const val TAG_PASTURE_WORKERS = "cd_workers"
    private const val TAG_BED_FACTOR = "cd_bed_factor"
    private const val TAG_BED_SLEEPER = "cd_sleeper"
    private const val TAG_WORKED_BY = "cd_worked_by"
    private const val TAG_WORKER_NAME = "name"
    private const val TAG_WORKER_ROLE = "role"
    private const val TAG_WORKER_STATUS = "status"

    override fun getUid(): ResourceLocation = UID

    override fun appendServerData(data: CompoundTag, accessor: BlockAccessor) {
        val level = accessor.level as? ServerLevel ?: return
        val pos = accessor.position
        val settings = WorkEngineSettings.current
        val engine = WorkEngine.get()
        val block = accessor.block
        if (block is PastureBlock) {
            val be = PastureAdapter.pastureAt(level, pos)
            if (be != null) {
                val modeData = PastureAdapter.workModeData(be)
                val area = PastureAdapter.workArea(level, be.blockPos, settings)
                val workers = PastureAdapter.tetheredIds(be).count { id -> engine?.worker(id) != null }
                data.putBoolean(TAG_PASTURE_PRESENT, true)
                data.putBoolean(TAG_PASTURE_MODE, modeData.enabled)
                data.putInt(TAG_PASTURE_RADIUS, area.horizontal)
                data.putBoolean(TAG_PASTURE_LOCKED, modeData.locked)
                data.putInt(TAG_PASTURE_WORKERS, workers)
            }
        }
        if (block is BedBlock) {
            val factor = settings.bedFactors.getOrElse(block.tier.tier - 1) { 1 }
            data.putInt(TAG_BED_FACTOR, factor)
            engine?.sleeperAt(level, pos)?.let { sleeper ->
                data.putString(TAG_BED_SLEEPER, sleeper.string)
            }
        }
        if (engine != null) {
            val worked = engine.workedAt(level, pos)
            if (worked.isNotEmpty()) {
                val list = ListTag()
                for (entry in worked) {
                    val item = CompoundTag()
                    item.putString(TAG_WORKER_NAME, entry.workerName.string)
                    item.putString(TAG_WORKER_ROLE, entry.roleId.path)
                    item.putString(TAG_WORKER_STATUS, entry.status.name)
                    list.add(item)
                }
                data.put(TAG_WORKED_BY, list)
            }
        }
    }

    override fun appendTooltip(tooltip: ITooltip, accessor: BlockAccessor, config: IPluginConfig) {
        val serverData = accessor.serverData
        if (accessor.block is PastureBlock && serverData.getBoolean(TAG_PASTURE_PRESENT)) {
            val lines = JadeTooltipRules.pastureLines(
                workMode = serverData.getBoolean(TAG_PASTURE_MODE),
                radius = serverData.getInt(TAG_PASTURE_RADIUS),
                locked = serverData.getBoolean(TAG_PASTURE_LOCKED),
                workerCount = serverData.getInt(TAG_PASTURE_WORKERS),
            )
            for (spec in lines) tooltip.add(spec.toComponent())
        }
        if (accessor.block is BedBlock && serverData.contains(TAG_BED_FACTOR, Tag.TAG_INT.toInt())) {
            val sleeper = serverData.getString(TAG_BED_SLEEPER).takeIf { it.isNotEmpty() }
            val lines = JadeTooltipRules.bedLines(serverData.getInt(TAG_BED_FACTOR), sleeper)
            for (spec in lines) tooltip.add(spec.toComponent())
        }
        if (serverData.contains(TAG_WORKED_BY, Tag.TAG_LIST.toInt())) {
            val raw = serverData.getList(TAG_WORKED_BY, Tag.TAG_COMPOUND.toInt())
            val entries = ArrayList<WorkedMachineEntry>(raw.size)
            for (i in 0 until raw.size) {
                val item = raw.getCompound(i)
                val statusName = item.getString(TAG_WORKER_STATUS)
                val status = WorkerStatus.entries.firstOrNull { it.name == statusName } ?: WorkerStatus.WORKING
                entries += WorkedMachineEntry(
                    workerName = item.getString(TAG_WORKER_NAME),
                    rolePath = item.getString(TAG_WORKER_ROLE),
                    status = status,
                )
            }
            for (spec in JadeTooltipRules.machineLines(entries)) {
                tooltip.add(spec.toComponent())
            }
        }
    }
}
