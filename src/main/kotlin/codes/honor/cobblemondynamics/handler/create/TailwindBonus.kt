package codes.honor.cobblemondynamics.handler.create

import com.simibubi.create.content.contraptions.bearing.WindmillBearingBlockEntity
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag

/**
 * The virtual sails of a Working Tailwind worker on one Create windmill bearing. Create puts them in every client packet of the
 * bearing, and the windmill mixin adds them to the sail count of the speed on both sides (CD-TLW-03, CD-TLW-07).
 */
class TailwindBonus(be: SmartBlockEntity) : BlockEntityBehaviour(be) {
    var sails: Int = 0
        private set

    /** The last tick that the bonus holds with no renewal. Server only. */
    private var until = Long.MIN_VALUE

    /** For this tick and the next. The handler renews it on every Working tick, so a missed release lapses within 2 ticks (CD-TLW-09). */
    fun grant(sails: Int, now: Long) {
        until = now + 1
        set(sails)
    }

    fun withdraw() {
        until = Long.MIN_VALUE
        set(0)
    }

    override fun tick() {
        super.tick()
        val level = blockEntity.level ?: return
        if (!level.isClientSide && sails != 0 && level.gameTime > until) withdraw()
    }

    /** CD-TLW-06: the network takes the new speed on this server tick, and every tracking client gets the bonus (CD-TLW-07). */
    private fun set(value: Int) {
        if (sails == value) return
        sails = value
        val bearing = blockEntity as? WindmillBearingBlockEntity ?: return
        if (bearing.level?.isClientSide == false) bearing.updateGeneratedRotation()
        bearing.sendData()
    }

    override fun getType(): BehaviourType<*> = TYPE

    override fun write(nbt: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) =
        TailwindNbt.write(nbt, sails, clientPacket)

    override fun read(nbt: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        sails = TailwindNbt.sails(nbt, clientPacket, sails)
    }

    companion object {
        @JvmField
        val TYPE = BehaviourType<TailwindBonus>("cobblemon_dynamics:tailwind")

        /** Null for a block entity that has none, and for a new bearing before its first tick, when Create attaches behaviours. */
        fun of(bearing: WindmillBearingBlockEntity?): TailwindBonus? = BlockEntityBehaviour.get(bearing, TYPE)

        /** What the windmill mixin counts: Create's real sails plus the bonus. It never lowers Create's own count. */
        @JvmStatic
        fun totalSails(bearing: WindmillBearingBlockEntity, realSails: Int): Int = realSails + (of(bearing)?.sails?.coerceAtLeast(0) ?: 0)
    }
}
