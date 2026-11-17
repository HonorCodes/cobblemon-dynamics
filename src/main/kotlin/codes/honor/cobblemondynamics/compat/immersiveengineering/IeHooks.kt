package codes.honor.cobblemondynamics.compat.immersiveengineering

import net.minecraft.world.level.block.entity.BlockEntity

/** What the two mill hooks of `mixin/compat/immersiveengineering` ask. Both run on both sides. */
object IeHooks {
    /**
     * CD-KIN-02: the torque of [wheel], which a Working Wheel Driver gives while IE finds none. It keeps IE's own [real] torque for
     * the demand of CD-KIN-03. Only the server computes a watermill's torque, and a client has no drive.
     */
    @JvmStatic
    fun watermillTorque(wheel: BlockEntity, real: Double): Double {
        val drive = wheel.getExistingDataOrNull(IeAttachments.WATERMILL_DRIVE.get()) ?: return real
        drive.realTorque = real
        return IeWatermillRule.torque(real, drive.driven, drive.torque, drive.direction)
    }

    /**
     * CD-AIR-05, CD-AIR-06: the turn speed of [windmill] with its worker's weather factor. IE sends the server's speed to the dynamo on
     * every tick, and a client animates by its synced copy of the bonus. On the server, a bonus that its worker no longer renews
     * lapses here, and the removal reaches the clients.
     */
    @JvmStatic
    fun windmillSpeed(windmill: BlockEntity, actual: Double): Double {
        val type = IeAttachments.WINDMILL_BONUS.get()
        val bonus = windmill.getExistingDataOrNull(type) ?: return actual
        val level = windmill.level ?: return actual
        if (!level.isClientSide && !IeWindmillMath.holds(bonus.until, level.gameTime)) {
            windmill.removeData(type)
            return actual
        }
        return IeWindmillMath.speed(actual, level.isRaining, level.isThundering, bonus.level)
    }
}
