package codes.honor.cobblemondynamics.handler.create

import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity

/**
 * The Fan Catalyst state of one Create encased fan. Create saves the mode with the fan, and puts the mode and the catalyst in every
 * client packet of the fan.
 */
class FanState(be: SmartBlockEntity) : BlockEntityBehaviour(be) {
    var fireMode: FireMode = FireMode.BLASTING
        private set

    /** The catalyst of the Working worker in front of the fan. Clients get it, so they build the same air current (CD-FAN-14). */
    var catalyst: FanCatalyst? = null
        private set

    /** The last tick that the catalyst holds with no renewal. Server only. */
    private var until = Long.MIN_VALUE

    fun catalyses(kind: FanCatalyst, mode: FireMode?): Boolean = catalyst == kind && (mode == null || mode == fireMode)

    /** For this tick and the next. The handler renews it on every Working tick, so a missed release lapses within 2 ticks. */
    fun grant(kind: FanCatalyst, now: Long) {
        until = now + 1
        if (catalyst != kind) {
            catalyst = kind
            changed()
        }
    }

    fun withdraw() {
        until = Long.MIN_VALUE
        if (catalyst != null) {
            catalyst = null
            changed()
        }
    }

    /** CD-FAN-10: the new mode, saved with the fan (CD-FAN-11). */
    fun flipMode(): FireMode {
        fireMode = fireMode.flipped()
        blockEntity.setChanged()
        changed()
        return fireMode
    }

    override fun tick() {
        super.tick()
        val level = blockEntity.level ?: return
        if (!level.isClientSide && catalyst != null && level.gameTime > until) withdraw()
    }

    /** The fan rebuilds its air current on its next server tick and sends it, with this state, to every client that tracks it. */
    private fun changed() {
        (blockEntity as? EncasedFanBlockEntity)?.blockInFrontChanged()
    }

    override fun getType(): BehaviourType<*> = TYPE

    override fun write(nbt: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) =
        FanNbt.write(nbt, fireMode, catalyst, clientPacket)

    override fun read(nbt: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        fireMode = FanNbt.mode(nbt)
        catalyst = FanNbt.catalyst(nbt, clientPacket, catalyst)
    }

    companion object {
        @JvmField
        val TYPE = BehaviourType<FanState>("cobblemon_dynamics:fan_state")

        /** Null for a block entity that has none, and for a new fan before its first tick, when Create attaches behaviours. */
        fun of(be: BlockEntity?): FanState? = BlockEntityBehaviour.get(be, TYPE)
    }
}
