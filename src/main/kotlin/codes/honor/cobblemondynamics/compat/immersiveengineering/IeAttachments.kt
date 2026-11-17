package codes.honor.cobblemondynamics.compat.immersiveengineering

import codes.honor.cobblemondynamics.MOD_ID
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.attachment.AttachmentType
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import net.neoforged.neoforge.registries.NeoForgeRegistries
import java.util.function.Supplier

/**
 * The block entity attachments of the IE mills. IE has no behaviour system, so the drive of a watermill and the bonus of a windmill
 * live here. Registered only while Immersive Engineering is loaded.
 */
object IeAttachments {
    private val REGISTER: DeferredRegister<AttachmentType<*>> = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID)

    /** On a watermill's master: its direction, saved only when reversed, and its worker's drive, never saved (CD-KIN-04). */
    val WATERMILL_DRIVE: DeferredHolder<AttachmentType<*>, AttachmentType<WatermillDrive>> =
        REGISTER.register("ie_watermill_drive", Supplier { watermillDrive() })

    /** On a windmill: the level of its Tailwind worker. NeoForge syncs it to the clients near the windmill. It is never saved (CD-AIR-06). */
    val WINDMILL_BONUS: DeferredHolder<AttachmentType<*>, AttachmentType<WindmillBonus>> =
        REGISTER.register("ie_windmill_bonus", Supplier { windmillBonus() })

    fun register(modBus: IEventBus) {
        REGISTER.register(modBus)
    }

    private fun watermillDrive(): AttachmentType<WatermillDrive> =
        AttachmentType.builder(Supplier { WatermillDrive() }).serialize(WatermillDrive.CODEC, WatermillDrive.SAVED).build()

    private fun windmillBonus(): AttachmentType<WindmillBonus> =
        AttachmentType.builder(Supplier { WindmillBonus(0) }).sync(WindmillBonus.STREAM_CODEC).build()
}
