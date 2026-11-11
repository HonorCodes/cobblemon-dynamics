package codes.honor.cobblemondynamics.registry

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.claim.LeashIndex
import codes.honor.cobblemondynamics.pasture.WorkModeData
import codes.honor.cobblemondynamics.pasture.WorkPastureIndex
import codes.honor.cobblemondynamics.stamina.StaminaView
import net.minecraft.core.BlockPos
import net.neoforged.neoforge.attachment.AttachmentType
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import net.neoforged.neoforge.registries.NeoForgeRegistries
import java.util.function.Supplier

object DynamicsAttachments {
    val REGISTER: DeferredRegister<AttachmentType<*>> = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID)

    /** On the pasture block entity. Off is the default and is not written, so only pastures in Work Mode carry it. */
    val WORK_MODE: DeferredHolder<AttachmentType<*>, AttachmentType<WorkModeData>> = REGISTER.register("work_mode", Supplier { workMode() })

    /** On the `ServerLevel`. */
    val WORK_PASTURES: DeferredHolder<AttachmentType<*>, AttachmentType<WorkPastureIndex>> =
        REGISTER.register("work_pastures", Supplier { workPastures() })

    /** On the `ServerLevel`: the leash bindings of the level by target (CD-CLM-06). */
    val LEASH_INDEX: DeferredHolder<AttachmentType<*>, AttachmentType<LeashIndex>> =
        REGISTER.register("leash_index", Supplier { leashIndex() })

    /**
     * On a worker's `PokemonEntity`: its Work Stamina and state for head bars. NeoForge syncs it to the clients that track the
     * entity, on each `setData` and `removeData`. It has no serializer, so it is never saved.
     */
    val STAMINA_VIEW: DeferredHolder<AttachmentType<*>, AttachmentType<StaminaView>> =
        REGISTER.register("stamina_view", Supplier { staminaView() })

    /** On a fan worker's `PokemonEntity`, for the length of its claim: the fan's position. Synced to tracking clients, never saved. */
    val FAN_WORKER: DeferredHolder<AttachmentType<*>, AttachmentType<BlockPos>> =
        REGISTER.register("fan_worker", Supplier { fanWorker() })

    private fun workMode(): AttachmentType<WorkModeData> =
        AttachmentType.builder(Supplier { WorkModeData.OFF }).serialize(WorkModeData.CODEC) { it.enabled }.build()

    private fun workPastures(): AttachmentType<WorkPastureIndex> =
        AttachmentType.builder(Supplier { WorkPastureIndex(HashSet()) }).serialize(WorkPastureIndex.CODEC).build()

    private fun leashIndex(): AttachmentType<LeashIndex> =
        AttachmentType.builder(Supplier { LeashIndex(HashMap()) }).serialize(LeashIndex.CODEC).build()

    private fun staminaView(): AttachmentType<StaminaView> =
        AttachmentType.builder(Supplier { StaminaView.FULL }).sync(StaminaView.STREAM_CODEC).build()

    private fun fanWorker(): AttachmentType<BlockPos> =
        AttachmentType.builder(Supplier { BlockPos.ZERO }).sync(BlockPos.STREAM_CODEC).build()
}
