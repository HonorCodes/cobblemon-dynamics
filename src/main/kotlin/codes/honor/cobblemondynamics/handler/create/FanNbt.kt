package codes.honor.cobblemondynamics.handler.create

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag

/** What a Fan Catalyst puts in front of a fan, in place of Create's lava or fire, water or soul fire. */
enum class FanCatalyst(val serialName: String) {
    FIRE("fire"),
    WATER("water"),
    GHOST("ghost"),
    ;

    companion object {
        fun parse(name: String?): FanCatalyst? = entries.firstOrNull { it.serialName == name }
    }
}

/** What a Fire catalyst does to the items in a fan's air current. Each fan keeps its own mode (CD-FAN-09). */
enum class FireMode(val serialName: String, val langKey: String) {
    BLASTING("blasting", "cobblemon_dynamics.fan.mode.blasting"),
    SMOKING("smoking", "cobblemon_dynamics.fan.mode.smoking"),
    ;

    /** CD-FAN-10 */
    fun flipped(): FireMode = if (this == BLASTING) SMOKING else BLASTING

    companion object {
        fun parse(name: String?): FireMode? = entries.firstOrNull { it.serialName == name }
    }
}

/** The NBT keys and rules of [FanState]. */
object FanNbt {
    const val MODE = "cd_fire_mode"
    const val CATALYST = "cd_catalyst"

    /** The mode always goes in (CD-FAN-11). The catalyst goes only into client packets, because claims are never saved (CD-CLM-07). */
    fun write(tag: CompoundTag, mode: FireMode, catalyst: FanCatalyst?, clientPacket: Boolean) {
        tag.putString(MODE, mode.serialName)
        if (clientPacket && catalyst != null) tag.putString(CATALYST, catalyst.serialName)
    }

    /** Blasting unless the tag says smoking (CD-FAN-09). */
    fun mode(tag: CompoundTag): FireMode = FireMode.parse(tag.getString(MODE)) ?: FireMode.BLASTING

    /** A client packet names the catalyst, or none. A save never does, so a load keeps [current]. */
    fun catalyst(tag: CompoundTag, clientPacket: Boolean, current: FanCatalyst?): FanCatalyst? =
        if (clientPacket) FanCatalyst.parse(tag.getString(CATALYST)) else current
}

/**
 * CD-FAN-03: a catalyst stands in the block in front of a fan's face, where it covers the whole air current. Only a fan that faces
 * sideways has such a block (ruling 2 of the Phase 2 plan): a worker in an up or down current would block it.
 */
object FanFront {
    fun of(fan: BlockPos, facing: Direction): BlockPos? = if (facing.axis.isHorizontal) fan.relative(facing) else null
}
