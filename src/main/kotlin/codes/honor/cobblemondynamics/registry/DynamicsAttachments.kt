package codes.honor.cobblemondynamics.registry

import codes.honor.cobblemondynamics.MOD_ID
import codes.honor.cobblemondynamics.pasture.WorkModeData
import codes.honor.cobblemondynamics.pasture.WorkPastureIndex
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

    private fun workMode(): AttachmentType<WorkModeData> =
        AttachmentType.builder(Supplier { WorkModeData.OFF }).serialize(WorkModeData.CODEC) { it.enabled }.build()

    private fun workPastures(): AttachmentType<WorkPastureIndex> =
        AttachmentType.builder(Supplier { WorkPastureIndex(HashSet()) }).serialize(WorkPastureIndex.CODEC).build()
}
