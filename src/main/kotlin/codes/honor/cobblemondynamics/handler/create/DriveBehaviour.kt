package codes.honor.cobblemondynamics.handler.create

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity

/**
 * The drive state of one Create water wheel, large water wheel or hand crank. Create saves the behaviour with its block entity and
 * puts it in every client packet of that block entity.
 */
class DriveBehaviour(be: SmartBlockEntity) : BlockEntityBehaviour(be) {
    /** +1 or −1, the sign of the speed that a worker gives the target. A new target turns at Create's positive speed. */
    var direction: Int = DriveNbt.FORWARD
        private set

    private var drivenNow = false

    /** True while a worker Works on the target. Clients get it so they compute the same flow; a save never holds it. */
    var driven: Boolean
        get() = drivenNow
        set(value) {
            if (drivenNow == value) return
            drivenNow = value
            blockEntity.sendData()
        }

    /** The first game tick on which the handler may read the target's real flow again. Not saved. */
    var nextFlowCheck: Long = 0

    /** The game tick on which the current drive of a hand crank began; its turns count from here. Not saved. */
    var drivenSince: Long = 0

    fun flip() {
        direction = -direction
        blockEntity.notifyUpdate()
    }

    override fun getType(): BehaviourType<*> = TYPE

    override fun write(nbt: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        DriveNbt.write(nbt, direction, drivenNow, clientPacket)
    }

    override fun read(nbt: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        direction = DriveNbt.direction(nbt)
        drivenNow = DriveNbt.driven(nbt, clientPacket, drivenNow)
    }

    companion object {
        @JvmField
        val TYPE = BehaviourType<DriveBehaviour>("cobblemon_dynamics:drive")

        /** Null for a block entity that has none, and for a new one before its first tick, when Create attaches behaviours. */
        fun of(be: BlockEntity?): DriveBehaviour? = BlockEntityBehaviour.get(be, TYPE)
    }
}

/** The NBT keys and rules of [DriveBehaviour]. */
object DriveNbt {
    const val DIRECTION = "cd_direction"
    const val DRIVEN = "cd_driven"
    const val FORWARD = 1

    /** The direction always goes in (CD-WHL-07, CD-CRK-08). The driven flag goes only into client packets, because claims are never saved (CD-CLM-07). */
    fun write(tag: CompoundTag, direction: Int, driven: Boolean, clientPacket: Boolean) {
        tag.putInt(DIRECTION, direction)
        if (clientPacket) tag.putBoolean(DRIVEN, driven)
    }

    /** +1 unless the tag holds a negative direction (CD-WHL-04, CD-CRK-05). */
    fun direction(tag: CompoundTag): Int = if (tag.getInt(DIRECTION) < 0) -FORWARD else FORWARD

    fun driven(tag: CompoundTag, clientPacket: Boolean, current: Boolean): Boolean = if (clientPacket) tag.getBoolean(DRIVEN) else current
}
