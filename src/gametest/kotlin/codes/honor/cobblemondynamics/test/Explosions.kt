package codes.honor.cobblemondynamics.test

import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Explosion
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB

/** Explosions in GameTests, with the drops that they make. */
internal object Explosions {
    /**
     * An explosion of radius 3 with drop decay, as a creeper's, at the centre of the block at [rel]. A block with an explosion resistance
     * of 6 there always breaks, and its own faces shield every other block and entity. A loot condition `minecraft:survives_explosion`
     * keeps a drop 1 time in 3 then. Returns the items that the explosion dropped, and removes them, so a later explosion cannot hit them.
     */
    fun creeperAt(helper: GameTestHelper, rel: BlockPos): List<ItemStack> {
        val pos = helper.absolutePos(rel)
        val explosion = helper.level.explode(null, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, 3.0f, Level.ExplosionInteraction.BLOCK)
        val interaction = explosion.blockInteraction
        helper.assertTrue(interaction == Explosion.BlockInteraction.DESTROY_WITH_DECAY, "the explosion has no drop decay: $interaction")
        val drops = helper.level.getEntitiesOfClass(ItemEntity::class.java, AABB(pos).inflate(2.0))
        val stacks = drops.map { it.item.copy() }
        for (drop in drops) drop.discard()
        return stacks
    }
}
