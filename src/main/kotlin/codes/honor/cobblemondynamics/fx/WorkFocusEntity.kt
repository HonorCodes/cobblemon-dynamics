package codes.honor.cobblemondynamics.fx

import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level

/**
 * What a Working worker's particles aim at, because Cobblemon particles aim only at entities (ADR-0007). One sits at the target
 * center for each Working claim. It is invisible, has no collision (CD-FX-05) and its type is never saved (CD-FX-06).
 */
class WorkFocusEntity(type: EntityType<WorkFocusEntity>, level: Level) : Entity(type, level) {
    init {
        noPhysics = true
        isInvisible = true
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {}

    override fun readAdditionalSaveData(tag: CompoundTag) {}

    override fun addAdditionalSaveData(tag: CompoundTag) {}

    override fun isPickable(): Boolean = false

    override fun canBeCollidedWith(): Boolean = false

    override fun isPushable(): Boolean = false
}
